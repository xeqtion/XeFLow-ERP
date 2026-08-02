package com.erp.system.service;

import com.erp.common.exception.BusinessException;
import com.erp.common.security.SecurityUtils;
import com.erp.common.security.TokenService;
import com.erp.common.storage.LocalFileStorage;
import com.erp.system.dto.SystemResetRequest;
import com.erp.system.dto.SystemResetResult;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 超级管理员个人中心中的系统重置能力。
 * 仅清理业务和运行数据，保留数据库结构、角色及权限配置。
 */
@Service
@Slf4j
public class SystemResetService {

    /**
     * 子表排在父表之前。表名均为编译期常量，不接受请求参数。
     */
    private static final List<String> OPERATIONAL_TABLES = List.of(
            "message_attachment",
            "user_message",
            "customer_file",
            "customer_extra_field",
            "bom_item",
            "sales_pi_item",
            "order_oem_file",
            "purchase_order_item",
            "shipping_instruction_item",
            "shipment_item",
            "import_row",
            "stock_movement",
            "personal_task",
            "sys_notification",
            "sys_invitation_code",
            "audit_log",
            "finance_entry",
            "shipment",
            "shipping_instruction",
            "purchase_order",
            "order_oem",
            "sales_pi",
            "contract_price",
            "standard_price",
            "bom",
            "product",
            "customer",
            "import_batch",
            "shared_file",
            "currency_rate"
    );

    private static final List<String> STORED_FILE_TABLES = List.of(
            "message_attachment",
            "customer_file",
            "order_oem_file",
            "shared_file"
    );

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final LocalFileStorage localFileStorage;
    private final ProfileService profileService;
    private final TokenService tokenService;

    public SystemResetService(SysUserMapper userMapper,
                              PasswordEncoder passwordEncoder,
                              JdbcTemplate jdbcTemplate,
                              LocalFileStorage localFileStorage,
                              ProfileService profileService,
                              TokenService tokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.localFileStorage = localFileStorage;
        this.profileService = profileService;
        this.tokenService = tokenService;
    }

    @Transactional(rollbackFor = Exception.class)
    public SystemResetResult reset(SystemResetRequest request) {
        Long currentUserId = requireCurrentSuperAdmin(request);
        boolean preserveUsers = Boolean.TRUE.equals(request.getPreserveUsers());

        Set<String> storedFiles = collectStoredFiles();
        List<String> discardedAvatars = preserveUsers
                ? List.of()
                : jdbcTemplate.queryForList(
                        "SELECT avatar FROM sys_user WHERE id <> ? AND avatar IS NOT NULL AND avatar <> ''",
                        String.class,
                        currentUserId);

        int cleared = 0;
        for (String table : OPERATIONAL_TABLES) {
            cleared += jdbcTemplate.update("DELETE FROM `" + table + "`");
        }

        if (!preserveUsers) {
            // 始终保留发起操作的超级管理员，保证重置后仍有可登录的启动账号。
            cleared += jdbcTemplate.update(
                    "DELETE FROM sys_user_role WHERE user_id <> ?",
                    currentUserId);
            cleared += jdbcTemplate.update("""
                    DELETE FROM sys_user_role
                    WHERE user_id = ?
                      AND role_id NOT IN (
                        SELECT id FROM sys_role
                        WHERE role_key = 'super_admin' AND deleted = 0
                      )
                    """, currentUserId);
            cleared += jdbcTemplate.update(
                    "DELETE FROM sys_user WHERE id <> ?",
                    currentUserId);
        }

        Long retainedUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE deleted = 0", Long.class);

        registerAfterCommit(() -> {
            try {
                storedFiles.forEach(localFileStorage::delete);
                discardedAvatars.forEach(profileService::deleteStoredAvatar);
            } catch (RuntimeException ex) {
                log.error("系统重置已提交，但部分业务附件清理失败", ex);
            }
            try {
                tokenService.invalidateAll();
            } catch (RuntimeException ex) {
                log.error("系统重置已提交，但登录会话批量失效失败", ex);
            }
        });

        return SystemResetResult.builder()
                .usersPreserved(preserveUsers)
                .currentSuperAdminRetained(true)
                .retainedUserCount(retainedUsers == null ? 0 : retainedUsers)
                .clearedRowCount(cleared)
                .completedAt(LocalDateTime.now())
                .build();
    }

    private Long requireCurrentSuperAdmin(SystemResetRequest request) {
        if (request == null || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("请输入超级管理员密码");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("未登录");
        }

        // 串行化系统重置，避免多个超级管理员同时选择清空用户。
        jdbcTemplate.queryForObject("""
                SELECT id FROM sys_role
                WHERE role_key = 'super_admin' AND deleted = 0
                FOR UPDATE
                """, Long.class);

        List<String> roles = userMapper.selectRoleKeysByUserId(currentUserId);
        if (roles == null || !roles.contains("super_admin")) {
            throw new BusinessException("仅超级管理员可重置系统数据");
        }

        SysUser current = userMapper.selectById(currentUserId);
        if (current == null || !passwordEncoder.matches(request.getPassword(), current.getPassword())) {
            throw new BusinessException("超级管理员密码不正确");
        }
        return currentUserId;
    }

    private Set<String> collectStoredFiles() {
        Set<String> files = new LinkedHashSet<>();
        for (String table : STORED_FILE_TABLES) {
            files.addAll(jdbcTemplate.queryForList(
                    "SELECT stored_name FROM `" + table
                            + "` WHERE stored_name IS NOT NULL AND stored_name <> ''",
                    String.class));
        }
        return files;
    }

    private void registerAfterCommit(Runnable cleanup) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanup.run();
            }
        });
    }
}
