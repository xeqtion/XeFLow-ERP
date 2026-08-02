package com.erp.system.bootstrap;

import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** Creates the only built-in super administrator on an empty installation. */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${erp.bootstrap-admin.enabled:false}")
    private boolean enabled;

    @Value("${erp.bootstrap-admin.password:}")
    private String password;

    @Value("${erp.bootstrap-admin.email:admin@example.com}")
    private String email;

    public BootstrapAdminInitializer(SysUserMapper userMapper,
                                     SysUserRoleMapper userRoleMapper,
                                     PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || userMapper.selectCount(null) > 0) {
            return;
        }
        validatePassword(password);

        LocalDateTime now = LocalDateTime.now();
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRealName("Super Administrator");
        admin.setNickname("admin");
        admin.setEmail(StringUtils.hasText(email) ? email.trim() : "admin@example.com");
        admin.setStatus(1);
        admin.setRemark("XeFlow bootstrap administrator");
        admin.setCreateTime(now);
        admin.setUpdateTime(now);
        admin.setDeleted(0);
        userMapper.insert(admin);

        SysUserRole role = new SysUserRole();
        role.setUserId(1L);
        role.setRoleId(0L);
        userRoleMapper.insert(role);
        log.info("XeFlow bootstrap administrator created. Change the password after first sign-in.");
    }

    private void validatePassword(String value) {
        boolean valid = StringUtils.hasText(value)
                && value.length() >= 12
                && value.chars().anyMatch(Character::isUpperCase)
                && value.chars().anyMatch(Character::isLowerCase)
                && value.chars().anyMatch(Character::isDigit)
                && value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!valid) {
            throw new IllegalStateException(
                    "ERP_BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 characters, "
                            + "including uppercase, lowercase, number and symbol");
        }
    }
}
