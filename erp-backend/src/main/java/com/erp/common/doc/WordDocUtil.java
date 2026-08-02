package com.erp.common.doc;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 轻量 Word 单据构建工具 (基于 POI XWPF, 程序化生成, 无需模板文件)。
 * 适合标题 + 若干键值行 + 明细表格的简单单据 (OEM 单 / PO / 出货指示 等)。
 */
public final class WordDocUtil {

    private WordDocUtil() {
    }

    /** 居中加粗标题 */
    public static void title(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(16);
        run.setText(text);
    }

    /** 普通文本行 */
    public static void line(XWPFDocument doc, String text) {
        doc.createParagraph().createRun().setText(text == null ? "" : text);
    }

    /** 创建表格并写入表头行 */
    public static XWPFTable table(XWPFDocument doc, String... headers) {
        XWPFTable table = doc.createTable();
        table.setWidth("100%");
        XWPFTableRow header = table.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell cell = i == 0 ? header.getCell(0) : header.addNewTableCell();
            setCell(cell, headers[i], true);
        }
        return table;
    }

    /** 追加一行数据 */
    public static void addRow(XWPFTable table, String... cells) {
        XWPFTableRow row = table.createRow();
        for (int i = 0; i < cells.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            if (cell == null) {
                cell = row.addNewTableCell();
            }
            setCell(cell, cells[i], false);
        }
    }

    /** 输出 docx 到 HTTP 响应并关闭文档 */
    public static void writeDocx(HttpServletResponse response, String downloadName, XWPFDocument doc)
            throws IOException {
        ensureNotEndingWithTable(doc);
        ensureSectPr(doc);
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String fileName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".docx");
        try (doc; OutputStream out = response.getOutputStream()) {
            doc.write(out);
        }
    }

    /**
     * Word 要求文档正文不能以表格结尾(表格后须有段落), 否则 MS Word 会判为内容损坏拒绝打开。
     * 若正文为空或最后一个元素是表格, 追加一个空段落兜底。
     */
    public static void ensureNotEndingWithTable(XWPFDocument doc) {
        var elements = doc.getBodyElements();
        if (elements.isEmpty()
                || elements.get(elements.size() - 1).getElementType() == BodyElementType.TABLE) {
            doc.createParagraph();
        }
    }

    /**
     * 确保正文存在节属性 <w:sectPr>(含页面尺寸/页边距)。
     * POI {@code new XWPFDocument()} 生成的最小文档缺少 sectPr, 严格的 MS Word 会判为
     * 内容损坏并弹出"修复"对话框(LibreOffice/python-docx 宽松能开)。补一个标准 A4 节即可。
     */
    public static void ensureSectPr(XWPFDocument doc) {
        CTBody body = doc.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSz.setW(BigInteger.valueOf(11906)); // A4 宽 (twips)
        pageSz.setH(BigInteger.valueOf(16838)); // A4 高 (twips)
        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        mar.setTop(BigInteger.valueOf(1440));
        mar.setBottom(BigInteger.valueOf(1440));
        mar.setLeft(BigInteger.valueOf(1440));
        mar.setRight(BigInteger.valueOf(1440));
    }

    /** 去除 XML 非法控制字符(保留 \t\n\r), 防脏数据生成无法打开的 docx */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void setCell(XWPFTableCell cell, String text, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setBold(bold);
        run.setText(sanitize(text));
    }
}
