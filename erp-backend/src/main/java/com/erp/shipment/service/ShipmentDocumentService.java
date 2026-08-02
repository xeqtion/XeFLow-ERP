package com.erp.shipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.order.entity.ShippingInstruction;
import com.erp.order.mapper.ShippingInstructionMapper;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;
import com.erp.sales.mapper.SalesPiItemMapper;
import com.erp.sales.mapper.SalesPiMapper;
import com.erp.shipment.entity.Shipment;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShipmentDocumentService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy");
    private static final String LOGO = "templates/images/xeflow-logo.png";

    @Value("${erp.company.name:XeFlow Demo Company}")
    private String companyName;

    @Value("${erp.company.address-line-1:Configure ERP_COMPANY_ADDRESS_LINE_1}")
    private String companyAddressLine1;

    @Value("${erp.company.address-line-2:}")
    private String companyAddressLine2;

    @Value("${erp.company.website:https://example.com}")
    private String companyWebsite;

    @Value("${erp.company.shipping-mark:XeFlow}")
    private String companyShippingMark;

    private final SalesPiMapper salesPiMapper;
    private final SalesPiItemMapper salesPiItemMapper;
    private final CustomerMapper customerMapper;
    private final ShippingInstructionMapper shippingInstructionMapper;

    public ShipmentDocumentService(SalesPiMapper salesPiMapper,
                                   SalesPiItemMapper salesPiItemMapper,
                                   CustomerMapper customerMapper,
                                   ShippingInstructionMapper shippingInstructionMapper) {
        this.salesPiMapper = salesPiMapper;
        this.salesPiItemMapper = salesPiItemMapper;
        this.customerMapper = customerMapper;
        this.shippingInstructionMapper = shippingInstructionMapper;
    }

    public void write(Shipment shipment, HttpServletResponse response) throws IOException {
        SalesPi pi = shipment.getPiId() != null ? salesPiMapper.selectById(shipment.getPiId()) : null;
        ShippingInstruction si = shipment.getSiId() != null ? shippingInstructionMapper.selectById(shipment.getSiId()) : null;
        Customer customer = findCustomer(pi, si);
        List<SalesPiItem> piItems = pi != null ? salesPiItemMapper.selectList(new LambdaQueryWrapper<SalesPiItem>()
                .eq(SalesPiItem::getPiId, pi.getId())
                .orderByAsc(SalesPiItem::getId)) : List.of();
        List<DocumentItem> items = buildItems(shipment, si, piItems);

        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Shipment Confirmation");
            sheet.setDisplayGridlines(false);
            configureColumns(sheet);
            Styles styles = new Styles(workbook);

            writeHeader(workbook, sheet, shipment, customer, styles);
            writeAddressAndRouting(sheet, shipment, pi, si, customer, styles);
            int nextRow = writeItems(sheet, items, 33, styles);
            writeTotals(sheet, nextRow + 18, shipment, items, styles);

            writeWorkbook(response, workbook, "Shipment_" + safeName(shipment.getShipmentNo()) + ".xls");
        }
    }

    private void configureColumns(Sheet sheet) {
        int[] widths = {8, 18, 18, 18, 18, 12, 10, 12, 12};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void writeHeader(Workbook workbook, Sheet sheet, Shipment shipment, Customer customer, Styles styles) throws IOException {
        merge(sheet, 1, 4, 0, 2);
        insertLogo(workbook, sheet);
        set(sheet, 6, 0, companyName, styles.normal);
        set(sheet, 7, 0, companyAddressLine1, styles.normal);
        set(sheet, 8, 0, companyAddressLine2, styles.normal);
        set(sheet, 9, 0, "Web: " + companyWebsite, styles.normal);

        merge(sheet, 1, 1, 5, 8);
        set(sheet, 1, 5, "Shipment Confirmation", styles.title);
        set(sheet, 4, 5, "Reference Number:", styles.headerLabel);
        set(sheet, 4, 7, shipment.getShipmentNo(), styles.headerValue);
        set(sheet, 5, 5, "Date:", styles.headerLabel);
        set(sheet, 5, 7, date(shipment.getShipDate()), styles.normal);
        set(sheet, 6, 5, "Customer ID:", styles.headerLabel);
        set(sheet, 6, 7, customer != null ? customer.getCustCode() : "", styles.normal);
    }

    private void insertLogo(Workbook workbook, Sheet sheet) throws IOException {
        for (int row = 1; row <= 4; row++) {
            Row sheetRow = sheet.getRow(row);
            if (sheetRow == null) {
                sheetRow = sheet.createRow(row);
            }
            sheetRow.setHeightInPoints(18);
        }
        ClassPathResource resource = new ClassPathResource(LOGO);
        byte[] bytes;
        try (var input = resource.getInputStream()) {
            bytes = input.readAllBytes();
        }
        int pictureIndex = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
        ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
        anchor.setCol1(0);
        anchor.setRow1(1);
        anchor.setCol2(3);
        anchor.setRow2(5);
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        sheet.createDrawingPatriarch().createPicture(anchor, pictureIndex);
    }

    private void writeAddressAndRouting(Sheet sheet, Shipment shipment, SalesPi pi, ShippingInstruction si,
                                        Customer customer, Styles styles) {
        section(sheet, 12, 0, 4, "SHIP TO:", styles);
        section(sheet, 12, 5, 8, "NOTE:", styles);
        List<String> address = customerAddress(customer, si);
        for (int i = 0; i < address.size(); i++) {
            set(sheet, 13 + i, 0, address.get(i), styles.normal);
        }
        merge(sheet, 13, 17, 5, 8);
        set(sheet, 13, 5, firstText(shipment.getRemark(), si != null ? si.getRemark() : null), styles.wrap);

        section(sheet, 22, 0, 1, "CONTACT", styles);
        section(sheet, 22, 2, 4, "SHIP VIA", styles);
        section(sheet, 22, 5, 6, "FOB POINT", styles);
        section(sheet, 22, 7, 8, "WAREHOUSE", styles);
        set(sheet, 23, 0, customer != null ? firstText(customer.getContactName(), customer.getContact()) : "", styles.normal);
        set(sheet, 23, 2, firstText(shipment.getContainerType(), ""), styles.normal);
        set(sheet, 23, 5, pi != null ? firstText(pi.getTradeTerm(), pi.getDestPort()) : "", styles.normal);
        set(sheet, 23, 7, firstText(shipment.getContainerNo(), companyShippingMark), styles.normal);

        section(sheet, 25, 0, 1, "SO TYPE", styles);
        section(sheet, 25, 2, 4, "SO NUMBER", styles);
        section(sheet, 25, 5, 8, "CUSTOMER P.O. NO.", styles);
        set(sheet, 26, 0, pi != null ? "PI" : "", styles.normal);
        set(sheet, 26, 2, pi != null ? pi.getPiNo() : "", styles.normal);
        set(sheet, 26, 5, firstText(si != null ? si.getOrderNo() : null, pi != null ? pi.getRemark() : null), styles.normal);
    }

    private int writeItems(Sheet sheet, List<DocumentItem> items, int row, Styles styles) {
        String[] headers = {"NO.", "ITEM", "", "", "", "QTY.", "UOM", "QTY. SHIPPED", "QTY. B/O"};
        for (int i = 0; i < headers.length; i++) {
            set(sheet, row, i, headers[i], styles.section);
        }
        merge(sheet, row, row, 1, 4);
        row++;

        for (int i = 0; i < items.size(); i++) {
            DocumentItem item = items.get(i);
            set(sheet, row, 0, i + 1, styles.normal);
            merge(sheet, row, row, 1, 4);
            set(sheet, row, 1, item.description(), styles.wrap);
            set(sheet, row, 5, item.qty(), styles.number);
            set(sheet, row, 6, firstText(item.unit(), "EACH"), styles.normal);
            set(sheet, row, 7, firstNumber(item.shippedQty(), item.qty()), styles.number);
            set(sheet, row, 8, item.backOrderQty(), styles.number);
            row++;
        }
        return row;
    }

    private void writeTotals(Sheet sheet, int row, Shipment shipment, List<DocumentItem> items, Styles styles) {
        setBottomBorder(sheet, row - 2, styles);
        BigDecimal totalQty = firstNumber(shipment.getQty(), sum(items.stream().map(DocumentItem::shippedQty).toList()));
        set(sheet, row, 5, "Total Qty:", styles.totalLabel);
        set(sheet, row, 8, totalQty, styles.number);
        set(sheet, row + 2, 5, "Total Weight (KG):", styles.totalLabel);
        set(sheet, row + 2, 8, firstNumber(shipment.getGrossWeight(), BigDecimal.ZERO), styles.number);
        set(sheet, row + 4, 5, "Total Volume (CBM):", styles.totalLabel);
        set(sheet, row + 4, 8, firstNumber(shipment.getVolumeCbm(), BigDecimal.ZERO), styles.number);
        set(sheet, row + 7, 8, "Page: 1 of 1", styles.normalRight);
    }

    private void setBottomBorder(Sheet sheet, int rowIndex, Styles styles) {
        for (int col = 0; col <= 8; col++) {
            set(sheet, rowIndex, col, "", styles.bottomLine);
        }
    }

    private Customer findCustomer(SalesPi pi, ShippingInstruction si) {
        Long customerId = pi != null ? pi.getCustomerId() : (si != null ? si.getCustomerId() : null);
        return customerId != null ? customerMapper.selectById(customerId) : null;
    }

    private List<DocumentItem> buildItems(Shipment shipment, ShippingInstruction si, List<SalesPiItem> piItems) {
        List<DocumentItem> items = new ArrayList<>();
        if (piItems != null && !piItems.isEmpty()) {
            BigDecimal totalPiQty = sum(piItems.stream().map(SalesPiItem::getQty).toList());
            boolean single = piItems.size() == 1;
            for (SalesPiItem item : piItems) {
                BigDecimal shipped = single ? firstNumber(shipment.getQty(), item.getQty()) : item.getQty();
                if (!single && shipment.getQty() != null && totalPiQty.signum() > 0) {
                    shipped = item.getQty() == null ? BigDecimal.ZERO
                            : item.getQty().multiply(shipment.getQty()).divide(totalPiQty, 4, java.math.RoundingMode.HALF_UP);
                }
                BigDecimal backOrder = item.getQty() == null || shipped == null ? BigDecimal.ZERO : item.getQty().subtract(shipped).max(BigDecimal.ZERO);
                items.add(new DocumentItem(
                        firstText(item.getProdCode(), item.getProductModel()) + ": " + firstText(item.getProductName(), item.getProductModel(), item.getRemark()),
                        firstNumber(item.getQty(), shipped),
                        firstText(item.getUnit(), "EACH"),
                        shipped,
                        backOrder));
            }
            return items;
        }
        if (si != null && StringUtils.hasText(si.getProductModel())) {
            BigDecimal qty = firstNumber(shipment.getQty(), si.getQuantity());
            items.add(new DocumentItem(
                    firstText(si.getProductModel(), si.getFactoryModel()),
                    qty,
                    "EACH",
                    qty,
                    BigDecimal.ZERO));
            return items;
        }
        items.add(new DocumentItem(firstText(shipment.getShipmentNo(), "Shipment item"),
                shipment.getQty(), "EACH", shipment.getQty(), BigDecimal.ZERO));
        return items;
    }

    private List<String> customerAddress(Customer customer, ShippingInstruction si) {
        List<String> lines = new ArrayList<>();
        if (customer != null) {
            add(lines, firstText(customer.getCompanyName(), customer.getCustNameEn(), customer.getCustNameCn(), customer.getCustCode()));
            add(lines, customer.getAddress());
            add(lines, customer.getCountry());
            add(lines, StringUtils.hasText(customer.getPhone()) ? "Phone: " + customer.getPhone() : "");
        } else if (si != null) {
            add(lines, si.getDeliveryAddress());
            add(lines, si.getOrderCountry());
            add(lines, StringUtils.hasText(si.getPhone()) ? "Phone: " + si.getPhone() : "");
        }
        return lines;
    }

    private void add(List<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private void section(Sheet sheet, int row, int firstCol, int lastCol, String value, Styles styles) {
        merge(sheet, row, row, firstCol, lastCol);
        set(sheet, row, firstCol, value, styles.section);
    }

    private void merge(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        if (firstRow != lastRow || firstCol != lastCol) {
            sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
        }
    }

    private void set(Sheet sheet, int rowIndex, int colIndex, Object value, CellStyle style) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            cell = row.createCell(colIndex);
        }
        if (value instanceof BigDecimal n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(value == null ? "" : String.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values == null ? BigDecimal.ZERO : values.stream()
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal firstNumber(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String date(LocalDate date) {
        return date == null ? "" : DATE_FORMAT.format(date);
    }

    private String safeName(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[\\\\/:*?\"<>|]", "_") : "document";
    }

    private void writeWorkbook(HttpServletResponse response, Workbook workbook, String fileName) throws IOException {
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded);
        workbook.write(response.getOutputStream());
        response.flushBuffer();
    }

    private record DocumentItem(String description, BigDecimal qty, String unit, BigDecimal shippedQty, BigDecimal backOrderQty) {
    }

    private static class Styles {
        final CellStyle title;
        final CellStyle normal;
        final CellStyle normalRight;
        final CellStyle headerLabel;
        final CellStyle headerValue;
        final CellStyle section;
        final CellStyle wrap;
        final CellStyle number;
        final CellStyle totalLabel;
        final CellStyle bottomLine;

        Styles(Workbook workbook) {
            title = style(workbook, (short) 20, true, null, false, HorizontalAlignment.CENTER, false);
            normal = style(workbook, (short) 11, false, null, false, HorizontalAlignment.LEFT, false);
            normalRight = style(workbook, (short) 11, false, null, false, HorizontalAlignment.RIGHT, false);
            headerLabel = style(workbook, (short) 11, true, null, false, HorizontalAlignment.LEFT, false);
            headerValue = style(workbook, (short) 11, true, null, false, HorizontalAlignment.LEFT, false);
            section = style(workbook, (short) 10, true, org.apache.poi.ss.usermodel.IndexedColors.PALE_BLUE.getIndex(), false, HorizontalAlignment.LEFT, false);
            wrap = style(workbook, (short) 10, false, null, false, HorizontalAlignment.LEFT, false);
            wrap.setWrapText(true);
            number = style(workbook, (short) 10, false, null, false, HorizontalAlignment.RIGHT, false);
            totalLabel = style(workbook, (short) 11, true, null, false, HorizontalAlignment.LEFT, false);
            bottomLine = style(workbook, (short) 10, false, null, true, HorizontalAlignment.LEFT, false);
        }

        private CellStyle style(Workbook workbook, short size, boolean bold, Short fill, boolean bottomBorder,
                                HorizontalAlignment align, boolean border) {
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(align);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            Font font = workbook.createFont();
            font.setFontName("Arial");
            font.setFontHeightInPoints(size);
            font.setBold(bold);
            style.setFont(font);
            if (fill != null) {
                style.setFillForegroundColor(fill);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (bottomBorder) {
                style.setBorderBottom(BorderStyle.MEDIUM);
            }
            if (border) {
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
            }
            return style;
        }
    }
}
