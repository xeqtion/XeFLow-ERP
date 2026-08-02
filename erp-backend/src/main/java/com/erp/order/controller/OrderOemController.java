package com.erp.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.order.entity.OrderOem;
import com.erp.order.entity.OrderOemFile;
import com.erp.order.service.OrderOemFileService;
import com.erp.order.service.OrderOemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 客户 OEM 要求 - CRUD、附件、OEM 单据生成。
 */
@Tag(name = "客户 OEM 要求")
@RestController
@RequestMapping("/order/oem")
public class OrderOemController {

    private static final DateTimeFormatter OEM_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final OrderOemService service;
    private final OrderOemFileService fileService;

    public OrderOemController(OrderOemService service,
                              OrderOemFileService fileService) {
        this.service = service;
        this.fileService = fileService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('order_oem:list')")
    @GetMapping("/page")
    public Result<PageResult<OrderOem>> page(PageQuery query) {
        return Result.success(PageResult.of(service.pageQuery(query)));
    }

    @Operation(summary = "详情")
    @PreAuthorize("@perm.has('order_oem:list')")
    @GetMapping("/{id}")
    public Result<OrderOem> get(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "新建")
    @OperationLog(module = "OEM要求", operation = "新建OEM")
    @PreAuthorize("@perm.has('order_oem:create')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody OrderOem entity) {
        entity.setId(null);
        entity.setOemNo(nextOemNo());
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("DRAFT");
        }
        service.save(entity);
        return Result.success(entity.getId());
    }

    @Operation(summary = "修改")
    @OperationLog(module = "OEM要求", operation = "修改OEM")
    @PreAuthorize("@perm.has('order_oem:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody OrderOem entity) {
        if (entity.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        OrderOem existing = service.getById(entity.getId());
        if (existing == null) {
            throw new BusinessException("OEM 记录不存在");
        }
        entity.setOemNo(existing.getOemNo());
        service.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "OEM要求", operation = "删除OEM")
    @PreAuthorize("@perm.has('order_oem:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.getById(id);
        fileService.deleteByOem(id);
        service.removeById(id);
        return Result.success();
    }

    @Operation(summary = "OEM 附件列表")
    @PreAuthorize("@perm.has('order_oem:list')")
    @GetMapping("/{id}/files")
    public Result<List<OrderOemFile>> files(@PathVariable Long id) {
        service.getById(id);
        return Result.success(fileService.listByOem(id));
    }

    @Operation(summary = "上传 OEM 附件")
    @OperationLog(module = "OEM要求", operation = "上传OEM附件")
    @PreAuthorize("@perm.has('order_oem:update')")
    @PostMapping("/{id}/files")
    public Result<OrderOemFile> uploadFile(@PathVariable Long id,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String remark) {
        return Result.success("上传成功", fileService.upload(service.getById(id), file, remark));
    }

    @Operation(summary = "下载 OEM 附件")
    @PreAuthorize("@perm.has('order_oem:list')")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        OrderOemFile file = visibleFile(fileId);
        Path path = fileService.resolvePath(file);
        if (!Files.exists(path)) {
            throw new BusinessException("文件已丢失");
        }
        Resource resource = new FileSystemResource(path);
        String fileName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + fileName)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "删除 OEM 附件")
    @OperationLog(module = "OEM要求", operation = "删除OEM附件")
    @PreAuthorize("@perm.has('order_oem:update')")
    @DeleteMapping("/files/{fileId}")
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        visibleFile(fileId);
        fileService.deleteFile(fileId);
        return Result.success();
    }

    private OrderOemFile visibleFile(Long fileId) {
        OrderOemFile file = fileService.getById(fileId);
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        service.getById(file.getOemId());
        return file;
    }

    private String nextOemNo() {
        String prefix = "OEM" + LocalDate.now().format(OEM_DATE) + "-";
        for (int i = 0; i < 100; i++) {
            String no = prefix + String.format("%04d", (System.currentTimeMillis() + i) % 10000);
            if (service.count(new LambdaQueryWrapper<OrderOem>().eq(OrderOem::getOemNo, no)) == 0) {
                return no;
            }
        }
        throw new BusinessException("OEM单号生成失败, 请稍后重试");
    }

}
