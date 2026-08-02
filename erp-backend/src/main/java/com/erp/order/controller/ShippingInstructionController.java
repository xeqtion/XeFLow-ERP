package com.erp.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.order.entity.ShippingInstruction;
import com.erp.order.service.ShippingInstructionDocumentService;
import com.erp.order.service.ShippingInstructionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "出货指示")
@RestController
@RequestMapping("/order/shipping-instruction")
public class ShippingInstructionController {

    private final ShippingInstructionService service;
    private final ShippingInstructionDocumentService documentService;

    public ShippingInstructionController(ShippingInstructionService service,
                                         ShippingInstructionDocumentService documentService) {
        this.service = service;
        this.documentService = documentService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('shipping_instruction:list')")
    @GetMapping("/page")
    public Result<PageResult<ShippingInstruction>> page(PageQuery query) {
        return Result.success(PageResult.of(service.pageQuery(query)));
    }

    @Operation(summary = "详情")
    @PreAuthorize("@perm.has('shipping_instruction:list')")
    @GetMapping("/{id}")
    public Result<ShippingInstruction> get(@PathVariable Long id) {
        return Result.success(service.getDetail(id));
    }

    @Operation(summary = "新建")
    @OperationLog(module = "出货指示", operation = "新建出货指示")
    @PreAuthorize("@perm.has('shipping_instruction:create')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ShippingInstruction entity) {
        if (StringUtils.hasText(entity.getSiNo())) {
            checkNoUnique(entity.getSiNo(), null);
        }
        entity.setId(null);
        service.save(entity);
        return Result.success(entity.getId());
    }

    @Operation(summary = "修改")
    @OperationLog(module = "出货指示", operation = "修改出货指示")
    @PreAuthorize("@perm.has('shipping_instruction:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody ShippingInstruction entity) {
        if (entity.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        checkNoUnique(entity.getSiNo(), entity.getId());
        service.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "出货指示", operation = "删除出货指示")
    @PreAuthorize("@perm.has('shipping_instruction:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success();
    }

    @Operation(summary = "生成出货指示单 Excel")
    @PreAuthorize("@perm.has('shipping_instruction:export')")
    @GetMapping("/{id}/document")
    public void document(@PathVariable Long id, HttpServletResponse response) throws IOException {
        ShippingInstruction si = service.getDetail(id);
        if (si == null) {
            throw new BusinessException("出货指示不存在");
        }
        documentService.write(si, response);
    }

    private void checkNoUnique(String siNo, Long excludeId) {
        if (!StringUtils.hasText(siNo)) {
            throw new BusinessException("出货指示单号不能为空");
        }
        LambdaQueryWrapper<ShippingInstruction> wrapper = new LambdaQueryWrapper<ShippingInstruction>()
                .eq(ShippingInstruction::getSiNo, siNo);
        if (excludeId != null) {
            wrapper.ne(ShippingInstruction::getId, excludeId);
        }
        if (service.count(wrapper) > 0) {
            throw new BusinessException("出货指示单号已存在: " + siNo);
        }
    }
}
