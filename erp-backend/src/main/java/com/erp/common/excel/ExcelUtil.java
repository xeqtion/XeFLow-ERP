package com.erp.common.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Excel 导入导出引擎 (基于 EasyExcel) - 全系统复用。
 *
 * <p>导出支持样式美化，包括标题、表头背景色、隔行变色、单元格合并、Logo 和印章。
 * 所有方法均保持向后兼容。
 *
 * <p>美化示例:
 * <pre>{@code
 * ExcelUtil.export(response, "产品资料", "产品", ProductExcel.class, data,
 *     ExcelStyleConfig.builder()
 *         .title("产品资料")
 *         .mergeColumnIndices(Set.of(0))
 *         .build());
 * }</pre>
 */
public final class ExcelUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExcelUtil.class);

    private ExcelUtil() {
    }

    // ========================================================================
    //  原始方法（无美化，向后兼容）
    // ========================================================================

    public static <T> void export(HttpServletResponse response, String fileName,
                                  String sheetName, Class<T> clazz, List<T> data) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment;filename*=utf-8''" + encoded + ".xlsx");
        EasyExcel.write(response.getOutputStream(), clazz)
                .sheet(sheetName == null ? "Sheet1" : sheetName)
                .doWrite(data);
    }

    public static <T> void template(HttpServletResponse response, String fileName,
                                    Class<T> clazz) throws IOException {
        export(response, fileName, "模板", clazz, new ArrayList<>());
    }

    // ========================================================================
    //  美化方法
    // ========================================================================

    /** 导出数据到 HTTP 响应（带样式美化） */
    public static <T> void export(HttpServletResponse response, String fileName,
                                  String sheetName, Class<T> clazz, List<T> data,
                                  ExcelStyleConfig styleConfig) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment;filename*=utf-8''" + encoded + ".xlsx");

        var writer = EasyExcel.write(response.getOutputStream(), clazz)
                .registerWriteHandler(new ExcelStyleWriteHandler(styleConfig))
                .sheet(sheetName == null ? "Sheet1" : sheetName);

        // 有标题行时，表头从第 1 行开始，第 0 行留给标题
        if (styleConfig.getTitle() != null && !styleConfig.getTitle().isEmpty()) {
            writer.relativeHeadRowIndex(1);
        }

        writer.doWrite(data);
    }

    /** 导出带表头样式的模板（不含标题行，确保导入兼容） */
    public static <T> void template(HttpServletResponse response, String fileName,
                                    Class<T> clazz,
                                    ExcelStyleConfig styleConfig) throws IOException {
        // 模板仅保留表头样式，去除标题、合并与图片
        ExcelStyleConfig cleanConfig = ExcelStyleConfig.builder()
                .headerBgColor(styleConfig.getHeaderBgColor())
                .headerFontColor(styleConfig.getHeaderFontColor())
                .headerFontSize(styleConfig.getHeaderFontSize())
                .headerBold(styleConfig.isHeaderBold())
                .headerHorizontalAlignment(styleConfig.getHeaderHorizontalAlignment())
                .contentFontSize(styleConfig.getContentFontSize())
                .showBorder(styleConfig.isShowBorder())
                .build();
        export(response, fileName, "模板", clazz, new ArrayList<>(), cleanConfig);
    }

    // ========================================================================
    //  导入
    // ========================================================================

    public static <T> void read(InputStream in, Class<T> clazz, int batchSize,
                                Consumer<List<T>> batchHandler) {
        EasyExcel.read(in, clazz, new PageReadListener<T>(batchHandler, batchSize)).sheet().doRead();
    }
}
