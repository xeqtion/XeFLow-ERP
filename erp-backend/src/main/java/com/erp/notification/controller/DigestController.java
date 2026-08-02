package com.erp.notification.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.notification.service.DigestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 经营摘要(周报/月报) - 手动预览与发送 (定时任务见 DigestService)
 */
@Tag(name = "经营摘要")
@RestController
@RequestMapping("/system/digest")
public class DigestController {

    private final DigestService digestService;

    public DigestController(DigestService digestService) {
        this.digestService = digestService;
    }

    @Operation(summary = "预览摘要 HTML (不发送)")
    @PreAuthorize("@perm.has('system:notification:send')")
    @GetMapping("/preview")
    public Result<Map<String, String>> preview(
            @RequestParam(defaultValue = "WEEKLY") DigestService.Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate[] range = resolveRange(period, startDate, endDate);
        String html = digestService.buildHtml(period, range[0], range[1]);
        String title = digestService.title(period, range[0], range[1]);
        return Result.success(Map.of("title", title, "html", html));
    }

    @Operation(summary = "发送摘要 (站内信 + 可选邮件)")
    @OperationLog(module = "经营摘要", operation = "发送周报月报")
    @PreAuthorize("@perm.has('system:notification:send')")
    @PostMapping("/send")
    public Result<Map<String, Object>> send(
            @RequestParam(defaultValue = "WEEKLY") DigestService.Period period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate[] range = resolveRange(period, startDate, endDate);
        int recipients = digestService.send(period, range[0], range[1]);
        return Result.success("已发送", Map.of(
                "recipients", recipients,
                "startDate", range[0].toString(),
                "endDate", range[1].toString()));
    }

    private LocalDate[] resolveRange(DigestService.Period period, LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            return new LocalDate[]{start, end};
        }
        return digestService.defaultRange(period);
    }
}
