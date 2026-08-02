package com.erp.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.system.auth.CodePurpose;
import com.erp.system.auth.RegisterProperties;
import com.erp.system.auth.VerificationCodeService;
import com.erp.system.dto.RegisterRequest;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 注册服务 - 注册闸门:
 * <ol>
 *   <li>系统级开关 (enabled)</li>
 *   <li>管理员生成的邀请码 (有效期与可用次数受数据库约束)</li>
 *   <li>邮箱域名白名单 (allowedDomains, 当前留空=不限制; 后续可切回公司邮箱)</li>
 *   <li>邮箱验证码 (requireEmailCode, 当前关闭)</li>
 *   <li>用户名 / 邮箱 唯一性</li>
 *   <li>分配默认低权限角色; 可选管理员审核后启用</li>
 * </ol>
 */
@Slf4j
@Service
public class RegisterService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService codeService;
    private final RegisterProperties props;
    private final InvitationCodeService invitationCodeService;

    public RegisterService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder,
                           VerificationCodeService codeService, RegisterProperties props,
                           InvitationCodeService invitationCodeService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.codeService = codeService;
        this.props = props;
        this.invitationCodeService = invitationCodeService;
    }

    /** 校验邮箱是否允许注册 (域名白名单); 供发码前预检, 避免给非公司邮箱发码。 */
    public void assertEmailRegistrable(String email) {
        if (!props.isEnabled()) {
            throw new BusinessException("系统当前未开放注册, 请联系管理员");
        }
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            throw new BusinessException("邮箱格式不正确");
        }
        String domain = email.trim().toLowerCase(Locale.ROOT);
        domain = domain.substring(domain.indexOf('@') + 1);
        if (!props.getAllowedDomains().isEmpty()) {
            boolean allowed = props.getAllowedDomains().stream()
                    .map(d -> d.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(domain::equals);
            if (!allowed) {
                throw new BusinessException("仅允许使用公司邮箱注册 (" + String.join(", ", props.getAllowedDomains()) + ")");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        assertEmailRegistrable(email);

        // 邮箱验证码 (一次性); 仅当开启"邮箱验证码"要求时校验 (当前阶段默认关闭)
        if (props.isRequireEmailCode()) {
            codeService.verify(email, CodePurpose.REGISTER, req.getCode());
        }

        // 唯一性
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, req.getUsername())) > 0) {
            throw new BusinessException("用户名已被占用");
        }
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmail, email)) > 0) {
            throw new BusinessException("该邮箱已注册");
        }

        // 默认角色
        SysRole role = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleKey, props.getDefaultRole()));
        if (role == null) {
            throw new BusinessException("默认角色未配置, 请联系管理员");
        }

        // 在用户写入前原子扣减邀请码。后续任一步失败时与注册事务一起回滚。
        invitationCodeService.consume(req.getRegisterCode());

        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setEmail(email);
        user.setRealName(StringUtils.hasText(req.getRealName()) ? req.getRealName().trim() : req.getUsername().trim());
        user.setNickname(user.getRealName());
        user.setPhone(StringUtils.hasText(req.getPhone()) ? req.getPhone().trim() : null);
        user.setStatus(props.isRequireApproval() ? 0 : 1);
        userMapper.insert(user);

        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);

        log.info("[REGISTER] 新用户注册: username={}, email={}, role={}, status={}",
                user.getUsername(), email, role.getRoleKey(), user.getStatus());
    }

    /** 注册后给前端的提示语 (区分是否需审核) */
    public String registeredMessage() {
        return props.isRequireApproval()
                ? "注册成功, 账号需管理员审核启用后方可登录"
                : "注册成功, 请登录";
    }

    /** 注册选项 - 供前端按当前策略渲染表单 (是否需注册码 / 邮箱验证码 / 域名限制) */
    public com.erp.system.dto.RegisterOptionsVO getOptions() {
        com.erp.system.dto.RegisterOptionsVO vo = new com.erp.system.dto.RegisterOptionsVO();
        vo.setEnabled(props.isEnabled());
        vo.setRequireRegisterCode(true);
        vo.setRequireEmailCode(props.isRequireEmailCode());
        vo.setAllowedDomains(props.getAllowedDomains());
        return vo;
    }
}
