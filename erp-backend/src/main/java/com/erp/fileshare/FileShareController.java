package com.erp.fileshare;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 资料共享 - 公司内部文件上传/下载/管理
 */
@Tag(name = "资料共享")
@RestController
@RequestMapping("/fileshare")
public class FileShareController {

    private final FileShareService fileShareService;

    public FileShareController(FileShareService fileShareService) {
        this.fileShareService = fileShareService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('fileshare:list')")
    @GetMapping("/page")
    public Result<PageResult<SharedFile>> page(PageQuery query) {
        return Result.success(PageResult.of(fileShareService.pageQuery(query)));
    }

    @Operation(summary = "上传文件")
    @OperationLog(module = "资料共享", operation = "上传文件")
    @PreAuthorize("@perm.has('fileshare:upload')")
    @PostMapping("/upload")
    public Result<SharedFile> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) String remark,
                                     @RequestParam(required = false) Integer expireDays) throws IOException {
        return Result.success("上传成功", fileShareService.upload(file, category, remark, expireDays));
    }

    @Operation(summary = "下载文件")
    @PreAuthorize("@perm.has('fileshare:list')")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        fileShareService.cleanupExpiredFiles();
        SharedFile f = fileShareService.getById(id);
        if (f == null) {
            throw new BusinessException("文件不存在");
        }
        Path path = fileShareService.resolvePath(f);
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

    @Operation(summary = "删除文件")
    @OperationLog(module = "资料共享", operation = "删除文件")
    @PreAuthorize("@perm.has('fileshare:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileShareService.deleteFile(id);
        return Result.success();
    }
}
