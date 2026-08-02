package com.erp.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.SecurityUtils;
import com.erp.system.dto.ChangePasswordDTO;
import com.erp.system.dto.DeactivateAccountDTO;
import com.erp.system.dto.ProfileUpdateDTO;
import com.erp.system.dto.ProfileVO;
import com.erp.system.dto.UserRoleItem;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileService {

    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final SysUserMapper userMapper;
    private final SysUserService userService;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(SysUserMapper userMapper, SysUserService userService, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    public ProfileVO current() {
        Long userId = currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        ProfileVO vo = new ProfileVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        List<UserRoleItem> roles = userMapper.selectRolesByUserIds(List.of(userId));
        vo.setRoles(roles.stream().map(UserRoleItem::getRoleKey).toList());
        vo.setRoleNames(roles.stream().map(UserRoleItem::getRoleName).toList());
        return vo;
    }

    public ProfileVO update(ProfileUpdateDTO dto) {
        Long userId = currentUserId();
        SysUser update = new SysUser();
        update.setId(userId);
        update.setRealName(trimToNull(dto.getRealName()));
        update.setNickname(trimToNull(dto.getNickname()));
        update.setPhone(trimToNull(dto.getPhone()));
        update.setRemark(trimToNull(dto.getRemark()));
        userMapper.updateById(update);
        return current();
    }

    public String uploadAvatar(MultipartFile file) {
        Long userId = currentUserId();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BusinessException("头像不能超过5MB");
        }
        String original = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "avatar";
        String ext = extension(original);
        if (!ALLOWED_AVATAR_EXT.contains(ext)) {
            throw new BusinessException("头像仅支持 jpg、png、gif、webp 格式");
        }
        if (StringUtils.hasText(file.getContentType()) && !file.getContentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException("头像文件类型不正确");
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.createDirectories(avatarRoot());
            file.transferTo(resolveAvatar(stored));
        } catch (IOException e) {
            throw new BusinessException("头像保存失败: " + e.getMessage());
        }
        SysUser old = userMapper.selectById(userId);
        SysUser update = new SysUser();
        update.setId(userId);
        update.setAvatar("/api/profile/avatar/" + stored);
        userMapper.updateById(update);
        deleteOldAvatar(old == null ? null : old.getAvatar());
        return update.getAvatar();
    }

    public Path resolveAvatar(String fileName) {
        Path root = avatarRoot();
        Path path = root.resolve(fileName == null ? "" : fileName).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException("非法头像路径");
        }
        return path;
    }

    public void changePassword(ChangePasswordDTO dto) {
        userService.changePassword(dto);
    }

    public void deactivate(DeactivateAccountDTO dto) {
        Long userId = currentUserId();
        if (SysUserService.ADMIN_USER_ID == userId) {
            throw new BusinessException("内置超级管理员不可注销");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("当前密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(0);
        userMapper.updateById(update);
    }

    private Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return userId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private Path avatarRoot() {
        return Paths.get("./uploads/avatars").toAbsolutePath().normalize();
    }

    private void deleteOldAvatar(String avatar) {
        deleteStoredAvatar(avatar);
    }

    /**
     * Best-effort deletion used after a committed system reset.
     */
    public void deleteStoredAvatar(String avatar) {
        if (!StringUtils.hasText(avatar) || !avatar.startsWith("/api/profile/avatar/")) {
            return;
        }
        String fileName = avatar.substring("/api/profile/avatar/".length());
        try {
            Files.deleteIfExists(resolveAvatar(fileName));
        } catch (Exception ignored) {
            // best effort cleanup
        }
    }
}
