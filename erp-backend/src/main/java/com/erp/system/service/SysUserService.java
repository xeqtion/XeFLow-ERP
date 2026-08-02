package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.SecurityUtils;
import com.erp.common.security.TokenService;
import com.erp.system.dto.ChangePasswordDTO;
import com.erp.system.dto.ResetPasswordDTO;
import com.erp.system.dto.SysUserQuery;
import com.erp.system.dto.SysUserSaveDTO;
import com.erp.system.dto.SysUserVO;
import com.erp.system.dto.UserRoleItem;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.entity.SysRole;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户管理服务 - CRUD + 角色分配 + 改密 / 重置密码 / 启用禁用
 */
public interface SysUserService extends IService<SysUser> {

    /** 内置超级管理员用户ID, 受保护不可禁用/删除 */
    long ADMIN_USER_ID = 1L;
    long SUPER_ADMIN_ROLE_ID = 0L;

    IPage<SysUserVO> pageVO(SysUserQuery query);

    SysUserVO getVO(Long id);

    void saveUser(SysUserSaveDTO dto);

    void updateUser(SysUserSaveDTO dto);

    void deleteUser(Long id);

    void resetPassword(ResetPasswordDTO dto);

    void changePassword(ChangePasswordDTO dto);

    void changeStatus(Long id, Integer status);

    @Service
    class Impl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

        private final SysUserRoleMapper userRoleMapper;
        private final SysRoleMapper roleMapper;
        private final PasswordEncoder passwordEncoder;
        private final TokenService tokenService;

        public Impl(SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper,
                    PasswordEncoder passwordEncoder, TokenService tokenService) {
            this.userRoleMapper = userRoleMapper;
            this.roleMapper = roleMapper;
            this.passwordEncoder = passwordEncoder;
            this.tokenService = tokenService;
        }

        @Override
        public IPage<SysUserVO> pageVO(SysUserQuery query) {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                    .select(SysUser.class, f -> !"password".equals(f.getProperty()))
                    .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                    .orderByDesc(SysUser::getCreateTime);
            if (StringUtils.hasText(query.getKeyword())) {
                String kw = query.getKeyword().trim();
                wrapper.and(w -> w.like(SysUser::getUsername, kw)
                        .or().like(SysUser::getRealName, kw)
                        .or().like(SysUser::getEmail, kw));
            }
            Page<SysUser> page = page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

            Page<SysUserVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            List<SysUserVO> vos = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
            attachRoles(vos);
            voPage.setRecords(vos);
            return voPage;
        }

        @Override
        public SysUserVO getVO(Long id) {
            SysUser user = getById(id);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            SysUserVO vo = toVO(user);
            attachRoles(List.of(vo));
            return vo;
        }

        @Override
        @Transactional(rollbackFor = Exception.class)
        public void saveUser(SysUserSaveDTO dto) {
            if (!StringUtils.hasText(dto.getPassword())) {
                throw new BusinessException("新增用户必须设置初始密码");
            }
            String email = normalizeEmail(dto.getEmail());
            checkUsernameUnique(dto.getUsername().trim(), null);
            checkEmailUnique(email, null);

            SysUser user = new SysUser();
            user.setUsername(dto.getUsername().trim());
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            user.setRealName(dto.getRealName());
            user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getRealName());
            user.setEmail(email);
            user.setPhone(dto.getPhone());
            user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
            user.setRemark(dto.getRemark());
            save(user);
            assignRoles(user.getId(), dto.getRoleIds());
        }

        @Override
        @Transactional(rollbackFor = Exception.class)
        public void updateUser(SysUserSaveDTO dto) {
            if (dto.getId() == null) {
                throw new BusinessException("缺少用户ID");
            }
            SysUser exist = getById(dto.getId());
            if (exist == null) {
                throw new BusinessException("用户不存在");
            }
            boolean protectedUser = dto.getId() == ADMIN_USER_ID;
            String username = protectedUser ? exist.getUsername() : dto.getUsername().trim();
            String email = normalizeEmail(dto.getEmail());
            checkUsernameUnique(username, dto.getId());
            checkEmailUnique(email, dto.getId());
            List<Long> targetRoleIds = resolveRoleIds(dto.getId(), dto.getRoleIds());
            boolean rolesChanged = !new HashSet<>(baseMapper.selectRoleIdsByUserId(dto.getId()))
                    .equals(new HashSet<>(targetRoleIds));
            Integer targetStatus = protectedUser ? 1 : dto.getStatus();
            boolean statusChanged = !Objects.equals(exist.getStatus(), targetStatus);
            boolean passwordChanged = StringUtils.hasText(dto.getPassword());

            SysUser user = new SysUser();
            user.setId(dto.getId());
            user.setUsername(username);
            user.setRealName(dto.getRealName());
            user.setNickname(dto.getNickname());
            user.setEmail(email);
            user.setPhone(dto.getPhone());
            user.setRemark(dto.getRemark());
            // 内置超管禁止被改为停用
            if (dto.getId() == ADMIN_USER_ID) {
                user.setStatus(1);
            } else {
                user.setStatus(dto.getStatus());
            }
            // 修改时密码留空 = 不改密
            if (passwordChanged) {
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
            }
            updateById(user);
            assignRoles(dto.getId(), targetRoleIds);
            if (rolesChanged || statusChanged || passwordChanged) {
                tokenService.invalidateUser(dto.getId());
            }
        }

        @Override
        @Transactional(rollbackFor = Exception.class)
        public void deleteUser(Long id) {
            if (id == ADMIN_USER_ID) {
                throw new BusinessException("内置超级管理员不可删除");
            }
            if (id.equals(SecurityUtils.getCurrentUserId())) {
                throw new BusinessException("不能删除当前登录账号");
            }
            removeById(id);
            userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
            tokenService.invalidateUser(id);
        }

        @Override
        public void resetPassword(ResetPasswordDTO dto) {
            SysUser exist = getById(dto.getUserId());
            if (exist == null) {
                throw new BusinessException("用户不存在");
            }
            SysUser update = new SysUser();
            update.setId(dto.getUserId());
            update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
            updateById(update);
            tokenService.invalidateUser(dto.getUserId());
        }

        @Override
        public void changePassword(ChangePasswordDTO dto) {
            Long userId = SecurityUtils.getCurrentUserId();
            if (userId == null) {
                throw new BusinessException("未登录");
            }
            SysUser user = getById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
                throw new BusinessException("原密码不正确");
            }
            if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
                throw new BusinessException("新密码不能与原密码相同");
            }
            SysUser update = new SysUser();
            update.setId(userId);
            update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
            updateById(update);
            tokenService.invalidateUser(userId);
        }

        @Override
        public void changeStatus(Long id, Integer status) {
            if (status == null || (status != 0 && status != 1)) {
                throw new BusinessException("状态值非法");
            }
            if (id == ADMIN_USER_ID && status == 0) {
                throw new BusinessException("内置超级管理员不可停用");
            }
            if (id.equals(SecurityUtils.getCurrentUserId()) && status == 0) {
                throw new BusinessException("不能停用当前登录账号");
            }
            if (getById(id) == null) {
                throw new BusinessException("用户不存在");
            }
            SysUser update = new SysUser();
            update.setId(id);
            update.setStatus(status);
            updateById(update);
            tokenService.invalidateUser(id);
        }

        // ---------------- 私有辅助 ----------------

        private SysUserVO toVO(SysUser user) {
            SysUserVO vo = new SysUserVO();
            BeanUtils.copyProperties(user, vo);
            return vo;
        }

        /** 为一批 VO 填充角色ID与名称 (一次查询) */
        private void attachRoles(List<SysUserVO> vos) {
            if (vos.isEmpty()) {
                return;
            }
            List<Long> ids = vos.stream().map(SysUserVO::getId).collect(Collectors.toList());
            List<UserRoleItem> items = baseMapper.selectRolesByUserIds(ids);
            Map<Long, List<UserRoleItem>> byUser = items.stream()
                    .collect(Collectors.groupingBy(UserRoleItem::getUserId));
            for (SysUserVO vo : vos) {
                List<UserRoleItem> rs = byUser.getOrDefault(vo.getId(), List.of());
                if (rs.stream().anyMatch(role -> "super_admin".equals(role.getRoleKey()))) {
                    rs = rs.stream().filter(role -> "super_admin".equals(role.getRoleKey())).toList();
                } else if (rs.stream().anyMatch(role -> "admin".equals(role.getRoleKey()))) {
                    rs = rs.stream().filter(role -> "admin".equals(role.getRoleKey())).toList();
                }
                vo.setRoleIds(rs.stream().map(UserRoleItem::getRoleId).collect(Collectors.toList()));
                vo.setRoleNames(rs.stream().map(UserRoleItem::getRoleName).collect(Collectors.toList()));
            }
        }

        private void assignRoles(Long userId, List<Long> roleIds) {
            userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
            for (Long rid : resolveRoleIds(userId, roleIds)) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(rid);
                userRoleMapper.insert(ur);
            }
        }

        private List<Long> resolveRoleIds(Long userId, List<Long> roleIds) {
            List<Long> distinct = roleIds == null
                    ? List.of()
                    : roleIds.stream().filter(Objects::nonNull).distinct().toList();
            if (Long.valueOf(ADMIN_USER_ID).equals(userId)) {
                return List.of(SUPER_ADMIN_ROLE_ID);
            }
            if (distinct.isEmpty()) {
                return List.of();
            }
            List<SysRole> found = roleMapper.selectBatchIds(distinct);
            if (found.size() != distinct.size()) {
                throw new BusinessException("选择的角色不存在或已被删除");
            }
            Map<Long, SysRole> byId = found.stream().collect(Collectors.toMap(SysRole::getId, role -> role));
            List<SysRole> selected = distinct.stream().map(byId::get).toList();
            if (selected.stream().anyMatch(role -> !Objects.equals(role.getStatus(), 1))) {
                throw new BusinessException("不能分配已停用的角色");
            }
            return UserRolePolicy.normalize(userId, selected);
        }

        private void checkUsernameUnique(String username, Long excludeId) {
            LambdaQueryWrapper<SysUser> w = Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, username);
            if (excludeId != null) {
                w.ne(SysUser::getId, excludeId);
            }
            if (count(w) > 0) {
                throw new BusinessException("用户名已存在: " + username);
            }
        }

        private void checkEmailUnique(String email, Long excludeId) {
            if (!StringUtils.hasText(email)) {
                return;
            }
            LambdaQueryWrapper<SysUser> w = Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getEmail, email);
            if (excludeId != null) {
                w.ne(SysUser::getId, excludeId);
            }
            if (count(w) > 0) {
                throw new BusinessException("邮箱已被占用: " + email);
            }
        }

        private String normalizeEmail(String email) {
            return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
        }
    }
}
