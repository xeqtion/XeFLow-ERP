package com.erp.shipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.doc.WordDocUtil;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.shipment.dto.ShipmentExcel;
import com.erp.shipment.entity.Shipment;
import com.erp.shipment.service.ShipmentDocumentService;
import com.erp.shipment.service.ShipmentService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 出货单 - CRUD + 分页 + Excel 导入导出
 */
@Tag(name = "出货管理")
@RestController
@RequestMapping("/shipment")
public class ShipmentController {

    private final ShipmentService service;
    private final ShipmentDocumentService documentService;

    public ShipmentController(ShipmentService service,
                              ShipmentDocumentService documentService) {
        this.service = service;
        this.documentService = documentService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('shipment:list')")
    @GetMapping("/page")
    public Result<PageResult<Shipment>> page(PageQuery query) {
        return Result.success(PageResult.of(service.pageQuery(query)));
    }

    @Operation(summary = "详情")
    @PreAuthorize("@perm.has('shipment:list')")
    @GetMapping("/{id}")
    public Result<Shipment> get(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "新建")
    @OperationLog(module = "出货管理", operation = "新建出货单")
    @PreAuthorize("@perm.has('shipment:create')")
    @PostMapping
    public Result<String> create(@Valid @RequestBody Shipment entity) {
        if (StringUtils.hasText(entity.getShipmentNo())) {
            checkNoUnique(entity.getShipmentNo(), null);
        }
        entity.setId(null);
        service.save(entity);
        return Result.id(entity.getId());
    }

    @Operation(summary = "修改")
    @OperationLog(module = "出货管理", operation = "修改出货单")
    @PreAuthorize("@perm.has('shipment:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody Shipment entity) {
        if (entity.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        checkNoUnique(entity.getShipmentNo(), entity.getId());
        service.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除")
    @OperationLog(module = "出货管理", operation = "删除出货单")
    @PreAuthorize("@perm.has('shipment:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success();
    }

    @Operation(summary = "Excel 导出")
    @PreAuthorize("@perm.has('shipment:export')")
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        List<ShipmentExcel> data = service.listByKeyword(keyword).stream().map(s -> {
            ShipmentExcel e = new ShipmentExcel();
            BeanUtils.copyProperties(s, e);
            return e;
        }).collect(Collectors.toList());
        ExcelUtil.export(response, "出货单", "出货单", ShipmentExcel.class, data);
    }

    @Operation(summary = "生成装箱单 (Word, 中英双语)")
    @PreAuthorize("@perm.has('shipment:export')")
    @GetMapping("/{id}/packing-list")
    public void packingList(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Shipment s = service.getById(id);
        if (s == null) {
            throw new BusinessException("出货单不存在");
        }
        XWPFDocument doc = new XWPFDocument();
        WordDocUtil.title(doc, "装箱单 / PACKING LIST");
        WordDocUtil.line(doc, "出货单号 / Shipment No.: " + nz(s.getShipmentNo()));
        WordDocUtil.line(doc, "出货日期 / Date: " + (s.getShipDate() != null ? s.getShipDate().toString() : ""));
        WordDocUtil.line(doc, "提单号 / B/L No.: " + nz(s.getBlNo()));
        WordDocUtil.line(doc, "柜型/柜号 / Container: " + nz(s.getContainerType()) + "  " + nz(s.getContainerNo()));
        WordDocUtil.line(doc, "");
        var table = WordDocUtil.table(doc, "项目 Item", "数值 Value");
        WordDocUtil.addRow(table, "数量 Qty", str(s.getQty()));
        WordDocUtil.addRow(table, "箱数 Packages", s.getPackageCount() != null ? String.valueOf(s.getPackageCount()) : "");
        WordDocUtil.addRow(table, "毛重 Gross Weight (kg)", str(s.getGrossWeight()));
        WordDocUtil.addRow(table, "净重 Net Weight (kg)", str(s.getNetWeight()));
        WordDocUtil.addRow(table, "体积 Volume (CBM)", str(s.getVolumeCbm()));
        WordDocUtil.writeDocx(response, "装箱单_" + nz(s.getShipmentNo()), doc);
    }

    @Operation(summary = "生成出货确认单(Excel模板)")
    @PreAuthorize("@perm.has('shipment:export')")
    @GetMapping("/{id}/document")
    public void document(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Shipment s = service.getById(id);
        if (s == null) {
            throw new BusinessException("出货单不存在");
        }
        documentService.write(s, response);
    }

    private String str(java.math.BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private void checkNoUnique(String shipmentNo, Long excludeId) {
        if (!StringUtils.hasText(shipmentNo)) {
            throw new BusinessException("出货单号不能为空");
        }
        LambdaQueryWrapper<Shipment> wrapper = new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getShipmentNo, shipmentNo);
        if (excludeId != null) {
            wrapper.ne(Shipment::getId, excludeId);
        }
        if (service.count(wrapper) > 0) {
            throw new BusinessException("出货单号已存在: " + shipmentNo);
        }
    }
}
