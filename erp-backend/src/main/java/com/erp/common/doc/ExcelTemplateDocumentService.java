package com.erp.common.doc;

import com.erp.basedata.entity.Customer;
import com.erp.basedata.entity.Product;
import com.erp.basedata.mapper.ProductMapper;
import com.erp.order.dto.PurchaseOrderDTO;
import com.erp.order.entity.PurchaseOrderItem;
import com.erp.sales.dto.SalesPiDTO;
import com.erp.sales.entity.SalesPiItem;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ExcelTemplateDocumentService {

    private static final String TEMPLATE = "templates/excel/pi_ci_po_template.xlsx";
    private static final String PI_SHEET = "Proforma invoice";
    private static final String CI_SHEET = "CI";
    private static final String PO_SHEET = "PO";

    private final ProductMapper productMapper;

    @Value("${erp.company.name:XeFlow Demo Company}")
    private String companyName;

    @Value("${erp.company.address-line-1:Configure ERP_COMPANY_ADDRESS_LINE_1}")
    private String companyAddressLine1;

    @Value("${erp.company.address-line-2:}")
    private String companyAddressLine2;

    @Value("${erp.company.website:https://example.com}")
    private String companyWebsite;

    @Value("${erp.company.contact:Configure ERP_COMPANY_CONTACT}")
    private String companyContact;

    @Value("${erp.company.bank.account-name:}")
    private String bankAccountName;

    @Value("${erp.company.bank.name:}")
    private String bankName;

    @Value("${erp.company.bank.account-no:}")
    private String bankAccountNo;

    @Value("${erp.company.bank.address:}")
    private String bankAddress;

    @Value("${erp.company.bank.swift-code:}")
    private String bankSwiftCode;

    @Value("${erp.company.bank.currency:USD}")
    private String bankCurrency;

    public ExcelTemplateDocumentService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public void writePi(SalesPiDTO pi, Customer customer, HttpServletResponse response) throws IOException {
        try (Workbook workbook = loadTemplate()) {
            Sheet sheet = keepOnlySheet(workbook, PI_SHEET);
            fillCompanyHeader(sheet);
            fillSalesHeader(sheet, pi, customer, "PROFORMA INVOICE");
            fillSalesItems(sheet, pi.getItems(), 21, 20, 41);
            fillBankInformation(sheet, 46);
            writeWorkbook(response, workbook, "PI_" + safeName(pi.getPiNo()) + ".xlsx");
        }
    }

    public void writeCommercialInvoice(SalesPiDTO pi, Customer customer, HttpServletResponse response) throws IOException {
        try (Workbook workbook = loadTemplate()) {
            Sheet sheet = keepOnlySheet(workbook, CI_SHEET);
            fillCompanyHeader(sheet);
            fillSalesHeader(sheet, pi, customer, "Commercial Invoice");
            set(sheet, "A20", "CHINA");
            set(sheet, "D20", firstText(pi.getDestPort(), customer != null ? customer.getCountry() : null));
            fillSalesItems(sheet, pi.getItems(), 24, 20, 44);
            fillBankInformation(sheet, 49);
            writeWorkbook(response, workbook, "CI_" + safeName(pi.getPiNo()) + ".xlsx");
        }
    }

    public void writePurchaseOrder(PurchaseOrderDTO po, HttpServletResponse response) throws IOException {
        try (Workbook workbook = loadTemplate()) {
            Sheet sheet = keepOnlySheet(workbook, PO_SHEET);
            fillCompanyHeader(sheet);
            set(sheet, "E1", "PURCHASE ORDER");
            set(sheet, "G3", po.getPoNo());
            set(sheet, "G4", po.getOrderDate());
            set(sheet, "G5", po.getSupplier());
            set(sheet, "G6", firstText(po.getCurrency(), "RMB"));
            set(sheet, "A12", po.getSupplier());
            set(sheet, "A18", po.getPoNo());
            set(sheet, "D18", po.getOrderDate());
            set(sheet, "F18", firstText(po.getPiNo(), po.getRemark()));
            set(sheet, "D20", po.getRemark());
            fillPoItems(sheet, po.getItems(), 21, 20, 41);
            writeWorkbook(response, workbook, "PO_" + safeName(po.getPoNo()) + ".xlsx");
        }
    }

    private Workbook loadTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource(TEMPLATE);
        try (InputStream in = resource.getInputStream()) {
            return WorkbookFactory.create(in);
        } catch (Exception e) {
            throw new IOException("读取单据 Excel 模板失败", e);
        }
    }

    private Sheet keepOnlySheet(Workbook workbook, String sheetName) {
        Sheet selected = workbook.getSheet(sheetName);
        if (selected == null) {
            throw new IllegalStateException("模板中缺少工作表: " + sheetName);
        }
        for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
            if (!workbook.getSheetName(i).equals(sheetName)) {
                workbook.removeSheetAt(i);
            }
        }
        workbook.setSheetName(0, sheetName);
        workbook.setForceFormulaRecalculation(true);
        return workbook.getSheetAt(0);
    }

    private void fillSalesHeader(Sheet sheet, SalesPiDTO pi, Customer customer, String title) {
        set(sheet, "E1", title);
        set(sheet, "G3", pi.getPiNo());
        set(sheet, "G4", pi.getSignDate());
        set(sheet, "G5", pi.getDeliveryDate());
        set(sheet, "G6", pi.getCustomerCode());
        set(sheet, "G7", firstText(pi.getCurrency(), "USD"));
        set(sheet, "G8", firstText(pi.getCustomerName(), customerName(customer)));

        String name = customerName(customer);
        String address = customer != null ? customer.getAddress() : "";
        String country = customer != null ? customer.getCountry() : "";
        String phone = customer != null ? customer.getPhone() : "";
        writeAddressBlock(sheet, "A", name, address, country, phone);
        writeAddressBlock(sheet, "F", name, address, country, phone);

        set(sheet, "A18", pi.getPiNo());
        set(sheet, "D18", firstText(pi.getTradeTerm(), pi.getDestPort()));
        set(sheet, "A20", deliveryLeadTime(pi.getSignDate(), pi.getDeliveryDate()));
        set(sheet, "D20", pi.getPaymentMethod());
    }

    private void fillCompanyHeader(Sheet sheet) {
        set(sheet, "A3", companyName);
        set(sheet, "A5", companyAddressLine1);
        set(sheet, "A6", companyAddressLine2);
        set(sheet, "A7", companyWebsite);
        set(sheet, "A8", companyContact);
    }

    private void fillBankInformation(Sheet sheet, int startRow) {
        set(sheet, "A" + startRow, "BANK INFORMATION");
        set(sheet, "A" + (startRow + 1), "Account Name: " + firstText(bankAccountName, companyName));
        set(sheet, "A" + (startRow + 2), "Bank Name: " + firstText(bankName, "Configure ERP_COMPANY_BANK_NAME"));
        set(sheet, "A" + (startRow + 3), "Account No.: " + firstText(bankAccountNo, "Configure ERP_COMPANY_BANK_ACCOUNT_NO"));
        set(sheet, "A" + (startRow + 4), "Bank Address: " + firstText(bankAddress, "Configure ERP_COMPANY_BANK_ADDRESS"));
        set(sheet, "A" + (startRow + 5), "SWIFT Code: " + firstText(bankSwiftCode, "Configure ERP_COMPANY_BANK_SWIFT_CODE"));
        set(sheet, "A" + (startRow + 6), "Currency: " + firstText(bankCurrency, "USD"));
    }

    private void writeAddressBlock(Sheet sheet, String col, String name, String address, String country, String phone) {
        set(sheet, col + "12", name);
        set(sheet, col + "13", address);
        set(sheet, col + "14", country);
        set(sheet, col + "15", StringUtils.hasText(phone) ? "Phone:" + phone : "");
    }

    private void fillSalesItems(Sheet sheet, List<SalesPiItem> items, int itemStartRow, int templateRows, int totalRow) {
        int count = items == null ? 0 : items.size();
        int extraRows = Math.max(0, count - templateRows);
        if (extraRows > 0) {
            sheet.shiftRows(totalRow, sheet.getLastRowNum(), extraRows, true, false);
        }
        prepareItemRows(sheet, itemStartRow, templateRows, count);
        for (int i = 0; i < count; i++) {
            SalesPiItem item = items.get(i);
            int row = itemStartRow + i;
            set(sheet, row, 0, i + 1);
            set(sheet, row, 1, item.getProdCode());
            set(sheet, row, 2, firstText(item.getProductName(), item.getProductModel(), item.getRemark()));
            set(sheet, row, 3, item.getQty());
            set(sheet, row, 4, item.getUnit());
            set(sheet, row, 5, item.getUnitPrice());
            set(sheet, row, 6, item.getAmount());
        }
        int shiftedTotal = totalRow + extraRows;
        int first = itemStartRow + 1;
        int last = itemStartRow + Math.max(count, 1);
        formula(sheet, shiftedTotal, 6, "SUM(G" + first + ":G" + last + ")");
        set(sheet, shiftedTotal + 1, 6, BigDecimal.ZERO);
        set(sheet, shiftedTotal + 2, 6, BigDecimal.ZERO);
        formula(sheet, shiftedTotal + 3, 6, "G" + (shiftedTotal + 1) + "+G" + (shiftedTotal + 2) + "-G" + (shiftedTotal + 3));
    }

    private void fillPoItems(Sheet sheet, List<PurchaseOrderItem> items, int itemStartRow, int templateRows, int totalRow) {
        int count = items == null ? 0 : items.size();
        int extraRows = Math.max(0, count - templateRows);
        if (extraRows > 0) {
            sheet.shiftRows(totalRow, sheet.getLastRowNum(), extraRows, true, false);
        }
        prepareItemRows(sheet, itemStartRow, templateRows, count);
        for (int i = 0; i < count; i++) {
            PurchaseOrderItem item = items.get(i);
            Product product = item.getProductId() != null ? productMapper.selectById(item.getProductId()) : null;
            int row = itemStartRow + i;
            set(sheet, row, 0, i + 1);
            set(sheet, row, 1, item.getProdCode());
            set(sheet, row, 2, product != null ? firstText(product.getProductName(), product.getCompanyModel(), item.getProdCode()) : item.getProdCode());
            set(sheet, row, 3, item.getQty());
            set(sheet, row, 4, product != null ? product.getUnit() : null);
            set(sheet, row, 5, item.getUnitPrice());
            set(sheet, row, 6, item.getAmount());
        }
        int shiftedTotal = totalRow + extraRows;
        int first = itemStartRow + 1;
        int last = itemStartRow + Math.max(count, 1);
        formula(sheet, shiftedTotal, 6, "SUM(G" + first + ":G" + last + ")");
        formula(sheet, shiftedTotal + 3, 6, "G" + (shiftedTotal + 1) + "+G" + (shiftedTotal + 2));
    }

    private void prepareItemRows(Sheet sheet, int itemStartRow, int templateRows, int itemCount) {
        Row styleRow = sheet.getRow(itemStartRow);
        for (int i = 0; i < Math.max(templateRows, itemCount); i++) {
            Row row = getOrCreateRow(sheet, itemStartRow + i);
            if (i >= templateRows) {
                copyRowStyle(styleRow, row);
            }
            for (int col = 0; col <= 6; col++) {
                Cell cell = getOrCreateCell(row, col);
                cell.setBlank();
            }
        }
    }

    private void copyRowStyle(Row source, Row target) {
        if (source == null || target == null) {
            return;
        }
        target.setHeight(source.getHeight());
        for (int i = 0; i <= 6; i++) {
            Cell sourceCell = source.getCell(i);
            if (sourceCell != null) {
                getOrCreateCell(target, i).setCellStyle(sourceCell.getCellStyle());
            }
        }
    }

    private void set(Sheet sheet, String address, Object value) {
        CellRef ref = CellRef.of(address);
        set(sheet, ref.row, ref.col, value);
    }

    private void set(Sheet sheet, int rowIndex, int colIndex, Object value) {
        Cell cell = getOrCreateCell(getOrCreateRow(sheet, rowIndex), colIndex);
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof LocalDate d) {
            cell.setCellValue(d.toString());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void formula(Sheet sheet, int rowIndex, int colIndex, String formula) {
        getOrCreateCell(getOrCreateRow(sheet, rowIndex), colIndex).setCellFormula(formula);
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private Cell getOrCreateCell(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        return cell != null ? cell : row.createCell(colIndex);
    }

    private String customerName(Customer customer) {
        if (customer == null) {
            return "";
        }
        return firstText(customer.getCompanyName(), customer.getCustNameEn(), customer.getCustNameCn(), customer.getCustCode());
    }

    private String deliveryLeadTime(LocalDate signDate, LocalDate deliveryDate) {
        if (signDate == null || deliveryDate == null) {
            return deliveryDate != null ? deliveryDate.toString() : "";
        }
        long days = ChronoUnit.DAYS.between(signDate, deliveryDate);
        return days > 0 ? days + " days after received order" : deliveryDate.toString();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeName(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[\\\\/:*?\"<>|]", "_") : "document";
    }

    private void writeWorkbook(HttpServletResponse response, Workbook workbook, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded);
        workbook.write(response.getOutputStream());
        response.flushBuffer();
    }

    private record CellRef(int row, int col) {
        static CellRef of(String address) {
            int split = 0;
            while (split < address.length() && Character.isLetter(address.charAt(split))) {
                split++;
            }
            String letters = address.substring(0, split).toUpperCase();
            int col = 0;
            for (int i = 0; i < letters.length(); i++) {
                col = col * 26 + (letters.charAt(i) - 'A' + 1);
            }
            int row = Integer.parseInt(address.substring(split));
            return new CellRef(row - 1, col - 1);
        }
    }
}
