package com.erp.common.excel;

import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

/**
 * Lightweight EasyExcel style handler for common exports.
 */
public class ExcelStyleWriteHandler implements CellWriteHandler {

    private final ExcelStyleConfig config;

    public ExcelStyleWriteHandler(ExcelStyleConfig config) {
        this.config = config == null ? ExcelStyleConfig.builder().build() : config;
    }

    @Override
    public void afterCellDispose(WriteSheetHolder writeSheetHolder,
                                 WriteTableHolder writeTableHolder,
                                 List<WriteCellData<?>> cellDataList,
                                 Cell cell,
                                 Head head,
                                 Integer relativeRowIndex,
                                 Boolean isHead) {
        Workbook workbook = writeSheetHolder.getSheet().getWorkbook();
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(cell.getCellStyle());
        style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

        Font font = workbook.createFont();
        if (Boolean.TRUE.equals(isHead)) {
            font.setBold(config.isHeaderBold());
            font.setFontHeightInPoints(config.getHeaderFontSize());
            font.setColor(config.getHeaderFontColor());
            style.setFillForegroundColor(config.getHeaderBgColor());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(defaultAlignment(config.getHeaderHorizontalAlignment()));
        } else {
            font.setFontHeightInPoints(config.getContentFontSize());
            style.setAlignment(HorizontalAlignment.LEFT);
        }
        style.setFont(font);

        if (config.isShowBorder()) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
        }
        cell.setCellStyle(style);
    }

    private HorizontalAlignment defaultAlignment(HorizontalAlignment alignment) {
        return alignment == null ? HorizontalAlignment.CENTER : alignment;
    }
}
