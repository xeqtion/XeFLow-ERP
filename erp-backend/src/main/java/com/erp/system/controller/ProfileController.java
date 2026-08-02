package com.erp.system.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.security.TokenService;
import com.erp.system.dto.ChangePasswordDTO;
import com.erp.system.dto.DeactivateAccountDTO;
import com.erp.system.dto.ProfileUpdateDTO;
import com.erp.system.dto.ProfileVO;
import com.erp.system.dto.SystemResetRequest;
import com.erp.system.dto.SystemResetResult;
import com.erp.system.service.ProfileService;
import com.erp.system.service.SystemResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final TokenService tokenService;
    private final SystemResetService systemResetService;

    public ProfileController(ProfileService profileService,
                             TokenService tokenService,
                             SystemResetService systemResetService) {
        this.profileService = profileService;
        this.tokenService = tokenService;
        this.systemResetService = systemResetService;
    }

    @GetMapping("/me")
    public Result<ProfileVO> me() {
        return Result.success(profileService.current());
    }

    @PutMapping("/me")
    @OperationLog(module = "个人中心", operation = "修改资料")
    public Result<ProfileVO> update(@Valid @RequestBody ProfileUpdateDTO dto) {
        return Result.success(profileService.update(dto));
    }

    @PutMapping("/password")
    @OperationLog(module = "个人中心", operation = "修改密码")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        profileService.changePassword(dto);
        return Result.success();
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @OperationLog(module = "个人中心", operation = "上传头像")
    public Result<Map<String, String>> avatar(@RequestParam MultipartFile file) {
        return Result.success(Map.of("avatar", profileService.uploadAvatar(file)));
    }

    @PostMapping("/deactivate")
    @OperationLog(module = "个人中心", operation = "注销账号")
    public Result<Void> deactivate(@Valid @RequestBody DeactivateAccountDTO dto, HttpServletRequest request) {
        profileService.deactivate(dto);
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            tokenService.remove(header.substring(7));
        }
        return Result.success();
    }

    @PostMapping("/system-reset")
    @OperationLog(module = "个人中心", operation = "系统重置")
    public Result<SystemResetResult> resetSystem(@Valid @RequestBody SystemResetRequest request) {
        return Result.success("系统数据重置完成", systemResetService.reset(request));
    }

    @GetMapping("/avatar/{fileName:.+}")
    public ResponseEntity<Resource> avatarFile(@PathVariable String fileName) throws Exception {
        Path path = profileService.resolveAvatar(fileName);
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(path);
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }
}
