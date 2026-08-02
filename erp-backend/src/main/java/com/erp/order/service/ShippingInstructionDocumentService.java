package com.erp.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.basedata.entity.Customer;
import com.erp.basedata.mapper.CustomerMapper;
import com.erp.order.entity.ShippingInstruction;
import com.erp.order.entity.ShippingInstructionItem;
import com.erp.sales.entity.SalesPi;
import com.erp.sales.entity.SalesPiItem;
import com.erp.sales.mapper.SalesPiItemMapper;
import com.erp.sales.mapper.SalesPiMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ShippingInstructionDocumentService {

    private static final String TEMPLATE = "templates/shipping-instruction.xlsx";
    private static final int DETAIL_ROW = 9;
    private static final int SUBTOTAL_ROW = 10;
    private static final int FOOTER_ROW = 11;

    private final SalesPiMapper salesPiMapper;
    private final SalesPiItemMapper salesPiItemMapper;
    private final CustomerMapper customerMapper;

    public ShippingInstructionDocumentService(SalesPiMapper salesPiMapper,
                                              SalesPiItemMapper salesPiItemMapper,
                                              CustomerMapper customerMapper) {
        this.salesPiMapper = salesPiMapper;
        this.salesPiItemMapper = salesPiItemMapper;
        this.customerMapper = customerMapper;
    }

    public void write(ShippingInstruction si, HttpServletResponse response) throws IOException {
        try (Workbook workbook = createWorkbook(si)) {
            writeWorkbook(response, workbook, "SI_" + safeName(si.getSiNo()) + ".xlsx");
        }
    }

    Workbook createWorkbook(ShippingInstruction si) throws IOException {
        ClassPathResource template = new ClassPathResource(TEMPLATE);
        try (InputStream inputStream = template.getInputStream()) {
            Workbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            workbook.setSheetName(workbook.getSheetIndex(sheet), "出货指示");
            sheet.setDisplayGridlines(false);

            SalesPi pi = si.getPiId() == null ? null : salesPiMapper.selectById(si.getPiId());
            List<DetailLine> details = loadDetailLines(si);
            int subtotalRow = prepareDetailRows(sheet, details.size());
            int footerRow = subtotalRow + 1;

            fillHeader(sheet, si);
            fillDetails(sheet, si, pi, details, subtotalRow);
            fillFooter(sheet, si, footerRow);

            workbook.setPrintArea(workbook.getSheetIndex(sheet), 1, 8, 2, footerRow);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.setForceFormulaRecalculation(true);
            return workbook;
        }
    }

    private void fillHeader(Sheet sheet, ShippingInstruction si) {
        setText(sheet, 2, 1, "出货编号：" + text(si.getSiNo()));
        setDate(sheet, 2, 2, si.getShipDate());
        setText(sheet, 3, 2, text(si.getShippingAddress()));
        setText(sheet, 4, 2, text(si.getDeliveryAddress()));
        setText(sheet, 5, 2, text(si.getContactName()));
        setText(sheet, 6, 2, text(si.getPhone()));
        setText(sheet, 7, 2, text(si.getOrderNo()));
    }

    private void fillDetails(Sheet sheet,
                             ShippingInstruction si,
                             SalesPi pi,
                             List<DetailLine> details,
                             int subtotalRow) {
        String customer = customerText(si);
        String shippingMarks = pi == null ? "" : text(pi.getShippingMarks());

        setText(sheet, DETAIL_ROW, 1, customer);

        for (int index = 0; index < details.size(); index++) {
            int rowIndex = DETAIL_ROW + index;
            DetailLine detail = details.get(index);
            setText(sheet, rowIndex, 2, detail.productModel());
            setText(sheet, rowIndex, 3, detail.factoryModel());
            setNumber(sheet, rowIndex, 4, detail.quantity());
            setNumber(sheet, rowIndex, 5, detail.cartonCount());
        }

        setText(sheet, DETAIL_ROW, 6, text(si.getOrderRequirement()));
        setText(sheet, DETAIL_ROW, 7, shippingMarks);
        setText(sheet, 3, 8, text(si.getRemark()));

        setText(sheet, subtotalRow, 1, "小计");
        setFormula(sheet, subtotalRow, 4,
                "SUM(E" + (DETAIL_ROW + 1) + ":E" + subtotalRow + ")");
        setFormula(sheet, subtotalRow, 5,
                "SUM(F" + (DETAIL_ROW + 1) + ":F" + subtotalRow + ")");
    }

    private void fillFooter(Sheet sheet, ShippingInstruction si, int footerRow) {
        setText(sheet, footerRow, 1, "业务：" + text(si.getOwnerName()));
        setText(sheet, footerRow, 2, "CBM（立方米）");
        setText(sheet, footerRow, 3, "");
        setText(sheet, footerRow, 5, "G.W（毛重）");
        setText(sheet, footerRow, 6, "");
        setText(sheet, footerRow, 7, "下单日期");
        setDate(sheet, footerRow, 8, si.getOrderDate());
    }

    private List<DetailLine> loadDetailLines(ShippingInstruction si) {
        if (si.getItems() != null && !si.getItems().isEmpty()) {
            return si.getItems().stream()
                    .map(this::toDetailLine)
                    .toList();
        }
        List<SalesPiItem> items = Collections.emptyList();
        if (si.getPiId() != null) {
            List<SalesPiItem> selected = salesPiItemMapper.selectList(new LambdaQueryWrapper<SalesPiItem>()
                    .eq(SalesPiItem::getPiId, si.getPiId())
                    .orderByAsc(SalesPiItem::getId));
            if (selected != null) {
                items = selected;
            }
        }
        if (items.isEmpty()) {
            BigDecimal quantity = si.getQuantity();
            return List.of(new DetailLine(
                    text(si.getProductModel()),
                    text(si.getFactoryModel()),
                    quantity,
                    firstNumber(si.getCartonCount(), quantity)));
        }

        List<BigDecimal> cartonCounts = allocateCartonCounts(items, si.getCartonCount());
        List<DetailLine> details = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            SalesPiItem item = items.get(index);
            details.add(new DetailLine(
                    text(item.getProductModel()),
                    text(item.getFactoryModel()),
                    item.getQty(),
                    cartonCounts.get(index)));
        }
        return details;
    }

    private DetailLine toDetailLine(ShippingInstructionItem item) {
        BigDecimal quantity = item == null ? null : item.getQuantity();
        return new DetailLine(
                item == null ? "" : text(item.getProductModel()),
                item == null ? "" : text(item.getFactoryModel()),
                quantity,
                item == null ? null : firstNumber(item.getCartonCount(), quantity));
    }

    private List<BigDecimal> allocateCartonCounts(List<SalesPiItem> items, BigDecimal totalCartons) {
        if (totalCartons == null) {
            return items.stream().map(SalesPiItem::getQty).toList();
        }
        if (items.size() == 1) {
            return List.of(totalCartons);
        }

        BigDecimal totalQuantity = items.stream()
                .map(SalesPiItem::getQty)
                .filter(value -> value != null && value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalQuantity.signum() <= 0) {
            List<BigDecimal> result = new ArrayList<>(Collections.nCopies(items.size(), BigDecimal.ZERO));
            result.set(0, totalCartons);
            return result;
        }

        int scale = Math.max(0, totalCartons.stripTrailingZeros().scale());
        BigDecimal allocated = BigDecimal.ZERO;
        List<BigDecimal> result = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            if (index == items.size() - 1) {
                result.add(totalCartons.subtract(allocated));
                continue;
            }
            BigDecimal quantity = items.get(index).getQty();
            BigDecimal cartons = quantity == null || quantity.signum() <= 0
                    ? BigDecimal.ZERO
                    : totalCartons.multiply(quantity).divide(totalQuantity, scale, RoundingMode.DOWN);
            result.add(cartons);
            allocated = allocated.add(cartons);
        }
        return result;
    }

    private int prepareDetailRows(Sheet sheet, int detailCount) {
        int count = Math.max(1, detailCount);
        removeMergedRegion(sheet, 9, 10, 6, 6);
        removeMergedRegion(sheet, 9, 10, 7, 7);
        removeMergedRegion(sheet, 3, 10, 8, 8);
        removeMergedRegion(sheet, 10, 10, 1, 3);
        removeMergedRegion(sheet, 11, 11, 3, 4);

        int additionalRows = count - 1;
        if (additionalRows > 0) {
            sheet.shiftRows(SUBTOTAL_ROW, sheet.getLastRowNum(), additionalRows, true, false);
            Row templateRow = sheet.getRow(DETAIL_ROW);
            for (int index = 1; index < count; index++) {
                copyRowStyle(sheet, templateRow, DETAIL_ROW + index);
            }
        }

        int subtotalRow = SUBTOTAL_ROW + additionalRows;
        int footerRow = FOOTER_ROW + additionalRows;
        if (count > 1) {
            sheet.addMergedRegion(new CellRangeAddress(DETAIL_ROW, subtotalRow - 1, 1, 1));
        }
        sheet.addMergedRegion(new CellRangeAddress(DETAIL_ROW, subtotalRow, 6, 6));
        sheet.addMergedRegion(new CellRangeAddress(DETAIL_ROW, subtotalRow, 7, 7));
        sheet.addMergedRegion(new CellRangeAddress(3, subtotalRow, 8, 8));
        sheet.addMergedRegion(new CellRangeAddress(subtotalRow, subtotalRow, 1, 3));
        sheet.addMergedRegion(new CellRangeAddress(footerRow, footerRow, 3, 4));
        return subtotalRow;
    }

    private void copyRowStyle(Sheet sheet, Row source, int targetIndex) {
        Row existing = sheet.getRow(targetIndex);
        if (existing != null) {
            sheet.removeRow(existing);
        }
        Row target = sheet.createRow(targetIndex);
        target.setHeight(source.getHeight());
        if (source.getRowStyle() != null) {
            target.setRowStyle(source.getRowStyle());
        }
        for (int column = source.getFirstCellNum(); column < source.getLastCellNum(); column++) {
            Cell sourceCell = source.getCell(column);
            if (sourceCell == null) {
                continue;
            }
            Cell targetCell = target.createCell(column);
            targetCell.setCellStyle(sourceCell.getCellStyle());
        }
    }

    private void removeMergedRegion(Sheet sheet,
                                    int firstRow,
                                    int lastRow,
                                    int firstColumn,
                                    int lastColumn) {
        for (int index = sheet.getNumMergedRegions() - 1; index >= 0; index--) {
            CellRangeAddress region = sheet.getMergedRegion(index);
            if (region.getFirstRow() == firstRow
                    && region.getLastRow() == lastRow
                    && region.getFirstColumn() == firstColumn
                    && region.getLastColumn() == lastColumn) {
                sheet.removeMergedRegion(index);
            }
        }
    }

    private String customerText(ShippingInstruction si) {
        Customer customer = si.getCustomerId() == null ? null : customerMapper.selectById(si.getCustomerId());
        String customerName = customer == null
                ? text(si.getCustomerCode())
                : firstText(customer.getCompanyName(), customer.getCustNameEn(), customer.getCustNameCn(),
                customer.getCustCode(), si.getCustomerCode());
        String country = text(si.getOrderCountry());
        if (!StringUtils.hasText(country) && customer != null) {
            country = text(customer.getCountry());
        }
        if (StringUtils.hasText(customerName) && StringUtils.hasText(country)) {
            return customerName + "（" + country + "）";
        }
        return firstText(customerName, country);
    }

    private void setText(Sheet sheet, int rowIndex, int colIndex, String value) {
        Cell cell = cell(sheet, rowIndex, colIndex);
        if (!StringUtils.hasText(value)) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(value);
    }

    private void setDate(Sheet sheet, int rowIndex, int colIndex, LocalDate value) {
        Cell cell = cell(sheet, rowIndex, colIndex);
        if (value == null) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(value);
    }

    private void setNumber(Sheet sheet, int rowIndex, int colIndex, BigDecimal value) {
        Cell cell = cell(sheet, rowIndex, colIndex);
        if (value == null) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(value.doubleValue());
    }

    private void setFormula(Sheet sheet, int rowIndex, int colIndex, String formula) {
        cell(sheet, rowIndex, colIndex).setCellFormula(formula);
    }

    private Cell cell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            cell = row.createCell(colIndex);
        }
        return cell;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private BigDecimal firstNumber(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
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

    private record DetailLine(String productModel,
                              String factoryModel,
                              BigDecimal quantity,
                              BigDecimal cartonCount) {
    }
}
