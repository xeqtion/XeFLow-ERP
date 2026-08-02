package com.erp.common.doc;

import com.deepoove.poi.XWPFTemplate;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 单据生成引擎 (基于 poi-tl Word 模板) - 全系统复用。
 * 模板放在 classpath:templates/doc/ 下, 用 {{占位符}} 填充业务数据,
 * 支持中英双语模板 (按 lang 选择不同模板文件)。
 *
 * 适用单据: 客户资料表 / PI / 合同 / OEM 单 / 出货指示 / PO / 月对账单 / 外贸业绩报告。
 */
@Service
public class DocumentService {

    private static final String TEMPLATE_DIR = "templates/doc/";

    /**
     * 渲染模板并写出到 HTTP 响应 (Word)。
     *
     * @param templateName 模板文件名, 如 pi_zh.docx / pi_en.docx
     * @param data         占位符数据
     * @param downloadName 下载文件名(不含扩展名)
     */
    public void render(String templateName, Map<String, Object> data,
                       String downloadName, HttpServletResponse response) throws IOException {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_DIR + templateName);
        if (!resource.exists()) {
            throw new IllegalArgumentException("单据模板不存在: " + templateName);
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment;filename*=utf-8''" + encoded + ".docx");
        try (InputStream in = resource.getInputStream();
             OutputStream out = response.getOutputStream();
             XWPFTemplate template = XWPFTemplate.compile(in).render(data)) {
            template.write(out);
        }
    }
}
