package com.erp.trace.controller;

import com.erp.common.api.Result;
import com.erp.trace.dto.PiBrief;
import com.erp.trace.dto.PiTraceVO;
import com.erp.trace.dto.TraceOwnerOption;
import com.erp.trace.service.TimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 单据追踪 - PI 全链路时间线
 */
@Tag(name = "单据追踪")
@RestController
@RequestMapping("/trace")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @Operation(summary = "可追踪的 PI 列表")
    @PreAuthorize("@perm.has('trace:view')")
    @GetMapping("/pi-list")
    public Result<List<PiBrief>> piList(@RequestParam(required = false) Long ownerId) {
        return Result.success(timelineService.recentPis(ownerId));
    }

    @Operation(summary = "单据追踪可筛选的合同担当")
    @PreAuthorize("@perm.has('trace:view')")
    @GetMapping("/owners")
    public Result<List<TraceOwnerOption>> owners() {
        return Result.success(timelineService.ownerOptions());
    }

    @Operation(summary = "按 PI 追踪全链路时间线")
    @PreAuthorize("@perm.has('trace:view')")
    @GetMapping("/pi/{id}")
    public Result<PiTraceVO> trace(@PathVariable Long id) {
        return Result.success(timelineService.trace(id));
    }
}
