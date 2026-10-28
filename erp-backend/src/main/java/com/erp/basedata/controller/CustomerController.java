package com.erp.basedata.controller;

import com.erp.basedata.dto.CustomerExcel;
import com.erp.basedata.dto.CustomerAssignDTO;
import com.erp.basedata.dto.CustomerAssigneeOption;
import com.erp.basedata.dto.CustomerPoolTransferDTO;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.entity.CustomerExtraField;
import com.erp.basedata.service.CustomerService;
import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.doc.WordDocUtil;
import com.erp.common.excel.ExcelUtil;
import com.erp.common.excel.ImportResult;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 客户资料 - 完整范例: CRUD + 分页 + Excel 导入导出 + 单据生成
 */
@Tag(name = "客户资料")
@RestController
@RequestMapping("/basedata/customer")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Operation(summary = "分页查询")
    @PreAuthorize("@perm.has('customer:list')")
    @GetMapping("/page")
    public Result<PageResult<Customer>> page(PageQuery query,
                                             @RequestParam(required = false) String poolType) {
        return Result.success(PageResult.of(customerService.pageQuery(query, poolType)));
    }

    @Operation(summary = "详情")
    @PreAuthorize("@perm.has('customer:list')")
    @GetMapping("/{id}")
    public Result<Customer> get(@PathVariable Long id) {
        return Result.success(customerService.getById(id));
    }

    @Operation(summary = "客户详情自定义键值")
    @PreAuthorize("@perm.has('customer:list')")
    @GetMapping("/{id}/extras")
    public Result<List<CustomerExtraField>> extras(@PathVariable Long id) {
        return Result.success(customerService.listExtraFields(id));
    }

    @Operation(summary = "保存客户详情自定义键值")
    @OperationLog(module = "客户资料", operation = "保存客户扩展资料")
    @PreAuthorize("@perm.has('customer:update')")
    @PutMapping("/{id}/extras")
    public Result<Void> saveExtras(@PathVariable Long id, @RequestBody List<CustomerExtraField> fields) {
        customerService.saveExtraFields(id, fields);
        return Result.success();
    }

    @Operation(summary = "新建")
    @OperationLog(module = "客户资料", operation = "新建客户")
    @PreAuthorize("@perm.has('customer:create')")
    @PostMapping
    public Result<String> create(@Valid @RequestBody Customer customer) {
        customerService.createCustomer(customer);
        return Result.id(customer.getId());
    }

    @Operation(summary = "修改")
    @OperationLog(module = "客户资料", operation = "修改客户")
    @PreAuthorize("@perm.has('customer:update')")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody Customer customer) {
        if (customer.getId() == null) {
            throw new BusinessException("缺少 id");
        }
        customerService.updateCustomer(customer);
        return Result.success();
    }

    @Operation(summary = "公共发展客户池分配跟单人")
    @OperationLog(module = "客户资料", operation = "分配公共客户池担当")
    @PreAuthorize("@perm.has('customer:update')")
    @PutMapping("/{id}/assignee")
    public Result<Void> assignAssignee(@PathVariable Long id, @Valid @RequestBody CustomerAssignDTO dto) {
        customerService.assignFromPublicPool(id, dto.getOwnerId());
        return Result.success();
    }

    @Operation(summary = "个人发展客户转入公共客户池")
    @OperationLog(module = "客户资料", operation = "客户转入公共客户池")
    @PreAuthorize("@perm.has('customer:update')")
    @PutMapping("/{id}/public-pool")
    public Result<Void> transferToPublicPool(@PathVariable Long id,
                                             @Valid @RequestBody CustomerPoolTransferDTO dto) {
        customerService.transferToPublicPool(id, dto.getReason());
        return Result.success();
    }

    @Operation(summary = "可分配跟单的业务员")
    @PreAuthorize("@perm.has('customer:update')")
    @GetMapping("/assignees")
    public Result<List<CustomerAssigneeOption>> assignees() {
        return Result.success(customerService.listAssignableSalesmen());
    }

    @Operation(summary = "删除")
    @OperationLog(module = "客户资料", operation = "删除客户")
    @PreAuthorize("@perm.has('customer:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return Result.success();
    }

    @Operation(summary = "Excel 导入")
    @OperationLog(module = "客户资料", operation = "导入客户")
    @PreAuthorize("@perm.has('customer:import')")
    @PostMapping("/import")
    public Result<ImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        return Result.success(customerService.importExcel(file));
    }

    @Operation(summary = "Excel 导出 (按关键字)")
    @PreAuthorize("@perm.has('customer:export')")
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String poolType,
                       HttpServletResponse response) throws IOException {
        List<CustomerExcel> data = customerService.listByKeyword(keyword, poolType).stream()
                .map(c -> {
                    CustomerExcel e = new CustomerExcel();
                    BeanUtils.copyProperties(c, e);
                    return e;
                }).collect(Collectors.toList());
        ExcelUtil.export(response, "客户资料", "客户", CustomerExcel.class, data);
    }

    @Operation(summary = "下载导入模板")
    @PreAuthorize("@perm.has('customer:export')")
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        ExcelUtil.template(response, "客户资料导入模板", CustomerExcel.class);
    }

    @Operation(summary = "生成客户资料表 (Word, 中英双语)")
    @PreAuthorize("@perm.has('customer:export')")
    @GetMapping("/{id}/document")
    public void document(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Customer c = customerService.getById(id);
        if (c == null) {
            throw new BusinessException("客户不存在");
        }
        // 经 WordDocUtil.writeDocx 统一输出: 会在表格后补空段落(否则 MS Word 判为内容损坏拒绝打开)
        WordDocUtil.writeDocx(response, "客户资料表_" + c.getCustCode(), buildCustomerDoc(c));
    }

    // ---------------- 私有辅助 ----------------

    private XWPFDocument buildCustomerDoc(Customer c) {
        XWPFDocument doc = new XWPFDocument();

        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(16);
        titleRun.setText("客户资料表 / Customer Information Sheet");

        XWPFTable table = doc.createTable();
        table.setWidth("100%");
        boolean first = true;
        String[][] fields = {
                {"客户ID / Customer ID", nz(c.getCustCode())},
                {"公司名称 / Company", nz(c.getCompanyName())},
                {"联系人姓名 / Contact", nz(c.getContactName())},
                {"职位 / Title", nz(c.getContactPosition())},
                {"国家 / Country", nz(c.getCountry())},
                {"地址 / Address", nz(c.getAddress())},
                {"币种 / Currency", nz(c.getCurrency())},
                {"网站 / Website", nz(c.getWebsite())},
                {"联系电话 / Phone", nz(c.getPhone())},
                {"电子邮箱 / Email", nz(c.getEmail())},
                {"社媒联系 / Social Contact", nz(c.getSocialContact())},
                {"备注 / Remark", nz(c.getRemark())}
        };
        for (String[] f : fields) {
            XWPFTableRow row = first ? table.getRow(0) : table.createRow();
            first = false;
            setCell(row.getCell(0), f[0], true);
            XWPFTableCell valueCell = row.getCell(1);
            if (valueCell == null) {
                valueCell = row.addNewTableCell();
            }
            setCell(valueCell, f[1], false);
        }
        // 设置首列宽度
        table.getRows().forEach(r -> {
            if (r.getCell(0) != null) {
                r.getCell(0).setWidth("35%");
            }
        });
        return doc;
    }

    private void setCell(XWPFTableCell cell, String text, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setBold(bold);
        run.setText(WordDocUtil.sanitize(text));
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
