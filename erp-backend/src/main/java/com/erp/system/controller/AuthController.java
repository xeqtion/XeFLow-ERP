package com.erp.system.controller;

import com.erp.common.api.Result;
import com.erp.system.auth.CodePurpose;
import com.erp.system.auth.VerificationCodeService;
import com.erp.system.dto.LoginRequest;
import com.erp.system.dto.LoginResponse;
import com.erp.system.dto.MenuVO;
import com.erp.system.dto.RegisterRequest;
import com.erp.system.dto.SendCodeRequest;
import com.erp.system.dto.SendCodeResponse;
import com.erp.system.dto.UserInfoVO;
import com.erp.system.service.AuthService;
import com.erp.system.service.MenuService;
import com.erp.system.service.RegisterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证接口 - 注册 / 登录(密码 / 邮箱验证码) / 注销 / 当前用户信息
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final MenuService menuService;
    private final RegisterService registerService;
    private final VerificationCodeService codeService;

    public AuthController(AuthService authService, MenuService menuService,
                          RegisterService registerService, VerificationCodeService codeService) {
        this.authService = authService;
        this.menuService = menuService;
        this.registerService = registerService;
        this.codeService = codeService;
    }

    @Operation(summary = "登录 (密码 / 邮箱验证码)")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "获取注册选项 (前端按需渲染注册表单)")
    @GetMapping("/register/options")
    public Result<com.erp.system.dto.RegisterOptionsVO> registerOptions() {
        return Result.success(registerService.getOptions());
    }

    @Operation(summary = "发送注册邮箱验证码 (仅当开启邮箱验证码要求时)")
    @PostMapping("/register/code")
    public Result<SendCodeResponse> sendRegisterCode(@Valid @RequestBody SendCodeRequest request) {
        registerService.assertEmailRegistrable(request.getEmail());
        String devCode = codeService.sendCode(request.getEmail(), CodePurpose.REGISTER);
        return Result.success("验证码已发送", new SendCodeResponse(devCode == null, devCode));
    }

    @Operation(summary = "注册 (公司邮箱 + 验证码)")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        registerService.register(request);
        return Result.success(registerService.registeredMessage(), null);
    }

    @Operation(summary = "发送登录邮箱验证码")
    @PostMapping("/login/code")
    public Result<SendCodeResponse> sendLoginCode(@Valid @RequestBody SendCodeRequest request) {
        String devCode = authService.sendLoginCode(request.getEmail());
        return Result.success("验证码已发送", new SendCodeResponse(devCode == null, devCode));
    }

    @Operation(summary = "注销")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return Result.success();
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        return Result.success(authService.getCurrentUserInfo());
    }

    @Operation(summary = "获取当前用户菜单树")
    @GetMapping("/menus")
    public Result<List<MenuVO>> menus() {
        return Result.success(menuService.getCurrentUserMenuTree());
    }
}
