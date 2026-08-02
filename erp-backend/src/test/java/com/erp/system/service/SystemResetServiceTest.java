package com.erp.system.service;

import com.erp.common.exception.BusinessException;
import com.erp.common.security.LoginUser;
import com.erp.common.security.TokenService;
import com.erp.common.storage.LocalFileStorage;
import com.erp.system.dto.SystemResetRequest;
import com.erp.system.dto.SystemResetResult;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemResetServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private LocalFileStorage localFileStorage;
    @Mock
    private ProfileService profileService;
    @Mock
    private TokenService tokenService;

    private SystemResetService service;

    @BeforeEach
    void setUp() {
        service = new SystemResetService(
                userMapper,
                passwordEncoder,
                jdbcTemplate,
                localFileStorage,
                profileService,
                tokenService);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("encoded");
        user.setStatus(1);
        LoginUser loginUser = new LoginUser(user, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        loginUser,
                        null,
                        loginUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resetsOperationalDataForSuperAdminAndInvalidatesAllSessions() {
        SystemResetRequest request = request(true, "secret");
        SysUser current = new SysUser();
        current.setId(1L);
        current.setPassword("encoded");

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("COUNT(*)")
                        ? 3L : 10L);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        when(jdbcTemplate.update(anyString())).thenReturn(1);
        when(userMapper.selectRoleKeysByUserId(1L)).thenReturn(List.of("super_admin"));
        when(userMapper.selectById(1L)).thenReturn(current);
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);

        SystemResetResult result = service.reset(request);

        assertThat(result.isUsersPreserved()).isTrue();
        assertThat(result.isCurrentSuperAdminRetained()).isTrue();
        assertThat(result.getRetainedUserCount()).isEqualTo(3);
        assertThat(result.getClearedRowCount()).isPositive();
        verify(tokenService).invalidateAll();
    }

    @Test
    void rejectsNonSuperAdministratorEvenWithCorrectPassword() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(10L);
        when(userMapper.selectRoleKeysByUserId(1L)).thenReturn(List.of("admin"));

        assertThatThrownBy(() -> service.reset(request(true, "secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅超级管理员可重置系统数据");

        verify(tokenService, never()).invalidateAll();
    }

    @Test
    void requiresPasswordBeforeTouchingDatabase() {
        assertThatThrownBy(() -> service.reset(request(true, "")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请输入超级管理员密码");

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class));
        verify(tokenService, never()).invalidateAll();
    }

    private SystemResetRequest request(boolean preserveUsers, String password) {
        SystemResetRequest request = new SystemResetRequest();
        request.setPreserveUsers(preserveUsers);
        request.setPassword(password);
        return request;
    }
}
