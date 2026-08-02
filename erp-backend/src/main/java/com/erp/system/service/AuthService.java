package com.erp.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.security.JwtUtil;
import com.erp.common.security.LoginUser;
import com.erp.common.security.SecurityUtils;
import com.erp.common.security.TokenService;
import com.erp.system.auth.CodePurpose;
import com.erp.system.auth.LoginAttemptService;
import com.erp.system.auth.VerificationCodeService;
import com.erp.system.dto.LoginRequest;
import com.erp.system.dto.LoginResponse;
import com.erp.system.dto.UserInfoVO;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 认证服务 - 登录(密码 / 邮箱验证码) / 注销 / 当前用户信息 / 发送登录验证码
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final SysUserMapper userMapper;
    private final UserDetailsService userDetailsService;
    private final VerificationCodeService codeService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                       TokenService tokenService, SysUserMapper userMapper,
                       UserDetailsService userDetailsService, VerificationCodeService codeService,
                       LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.userDetailsService = userDetailsService;
        this.codeService = codeService;
        this.loginAttemptService = loginAttemptService;
    }

    public LoginResponse login(LoginRequest request) {
        if ("code".equalsIgnoreCase(request.getLoginType())) {
            return loginByEmailCode(request);
        }
        return loginByPassword(request);
    }

    private LoginResponse loginByPassword(LoginRequest request) {
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("用户名和密码不能为空");
        }
        String username = request.getUsername().trim();
        if (loginAttemptService.isLocked(username)) {
            throw new BusinessException("登录失败次数过多, 账号已锁定, 请 "
                    + loginAttemptService.getLockMinutes() + " 分钟后再试");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (DisabledException e) {
            throw new BusinessException("账号已禁用或待管理员审核");
        } catch (BadCredentialsException e) {
            int remaining = loginAttemptService.recordFailure(username);
            throw new BusinessException(remaining > 0
                    ? "用户名或密码错误, 还可尝试 " + remaining + " 次"
                    : "密码错误次数过多, 账号已锁定 " + loginAttemptService.getLockMinutes() + " 分钟");
        }
        loginAttemptService.reset(username);
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        return issueToken(loginUser);
    }

    private LoginResponse loginByEmailCode(LoginRequest request) {
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("邮箱不能为空");
        }
        SysUser user = findUserByEmail(email);
        // 校验验证码 (一次性)
        codeService.verify(email, CodePurpose.LOGIN, request.getCode());
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已禁用或待管理员审核");
        }
        LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(user.getUsername());
        return issueToken(loginUser);
    }

    private LoginResponse issueToken(LoginUser loginUser) {
        String token = jwtUtil.generateToken(loginUser.getUserId(), loginUser.getUsername());
        tokenService.cache(token, loginUser);
        return new LoginResponse(token);
    }

    /**
     * 发送"邮箱验证码登录"的验证码; 邮箱必须对应已存在且启用的唯一账号。
     *
     * @return 降级日志模式下返回验证码本身(便于本地联调), 真实发信返回 null
     */
    public String sendLoginCode(String email) {
        String normalized = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        findUserByEmail(normalized); // 不存在/重复/禁用直接抛异常
        return codeService.sendCode(normalized, CodePurpose.LOGIN);
    }

    /** 按邮箱定位唯一启用账号; 不存在/重复均抛业务异常 */
    private SysUser findUserByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("邮箱不能为空");
        }
        List<SysUser> users = userMapper.selectList(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmail, email));
        if (users.isEmpty()) {
            throw new BusinessException("该邮箱未注册");
        }
        if (users.size() > 1) {
            throw new BusinessException("该邮箱关联多个账号, 请改用用户名密码登录");
        }
        return users.get(0);
    }

    public void logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            tokenService.remove(header.substring(7));
        }
    }

    public UserInfoVO getCurrentUserInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException("未登录");
        }
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(loginUser.getUserId());
        vo.setUsername(loginUser.getUsername());
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        vo.setRealName(user.getRealName());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setRoles(userMapper.selectRoleKeysByUserId(loginUser.getUserId()));
        vo.setPermissions(loginUser.getPermissions());
        return vo;
    }
}
