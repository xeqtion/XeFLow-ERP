package com.erp.basedata.controller;

import com.erp.basedata.entity.CustomerFile;
import com.erp.basedata.service.CustomerFileService;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 客户附件 - 客户详细资料文件上传/下载/删除 (支持 PDF/图片/表格等任意类型)
 */
@Tag(name = "客户附件")
@RestController
@RequestMapping("/basedata/customer")
public class CustomerFileController {

    private final CustomerFileService service;

    public CustomerFileController(CustomerFileService service) {
        this.service = service;
    }

    @Operation(summary = "客户附件列表")
    @PreAuthorize("@perm.has('customer:list')")
    @GetMapping("/{customerId}/files")
    public Result<List<CustomerFile>> list(@PathVariable Long customerId) {
        return Result.success(service.listByCustomer(customerId));
    }

    @Operation(summary = "上传客户附件")
    @OperationLog(module = "客户资料", operation = "上传客户附件")
    @PreAuthorize("@perm.has('customer:update')")
    @PostMapping("/{customerId}/files")
    public Result<CustomerFile> upload(@PathVariable Long customerId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) String remark) {
        return Result.success("上传成功", service.upload(customerId, file, remark));
    }

    @Operation(summary = "下载客户附件")
    @PreAuthorize("@perm.has('customer:list')")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long fileId) {
        CustomerFile f = service.getById(fileId);
        if (f == null) {
            throw new BusinessException("文件不存在");
        }
        Path path = service.resolvePath(f);
        if (!Files.exists(path)) {
            throw new BusinessException("文件已丢失");
        }
        Resource resource = new FileSystemResource(path);
        String fileName = URLEncoder.encode(f.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + fileName)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "删除客户附件")
    @OperationLog(module = "客户资料", operation = "删除客户附件")
    @PreAuthorize("@perm.has('customer:update')")
    @DeleteMapping("/files/{fileId}")
    public Result<Void> delete(@PathVariable Long fileId) {
        service.deleteFile(fileId);
        return Result.success();
    }
}
