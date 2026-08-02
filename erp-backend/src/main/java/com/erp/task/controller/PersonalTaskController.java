package com.erp.task.controller;

import com.erp.common.api.Result;
import com.erp.task.entity.PersonalTask;
import com.erp.task.service.PersonalTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * 个人待办 - 首页日历计划与待处理事项。每个用户仅操作自己的待办。
 */
@Tag(name = "个人待办")
@RestController
@RequestMapping("/task")
public class PersonalTaskController {

    private final PersonalTaskService service;

    public PersonalTaskController(PersonalTaskService service) {
        this.service = service;
    }

    @Operation(summary = "按月查询(日历)")
    @GetMapping("/calendar")
    public Result<List<PersonalTask>> calendar(@RequestParam(required = false) String month) {
        YearMonth ym = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month);
        return Result.success(service.listInRange(ym.atDay(1), ym.atEndOfMonth()));
    }

    @Operation(summary = "按区间查询")
    @GetMapping("/range")
    public Result<List<PersonalTask>> range(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.success(service.listInRange(start, end));
    }

    @Operation(summary = "我的待办(未完成)")
    @GetMapping("/pending")
    public Result<List<PersonalTask>> pending() {
        return Result.success(service.listPending());
    }

    @Operation(summary = "统计(待办/今日/逾期)")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(service.stats());
    }

    @Operation(summary = "新建待办")
    @PostMapping
    public Result<Long> create(@RequestBody PersonalTask task) {
        return Result.success(service.createTask(task));
    }

    @Operation(summary = "修改待办")
    @PutMapping
    public Result<Void> update(@RequestBody PersonalTask task) {
        service.updateTask(task);
        return Result.success();
    }

    @Operation(summary = "切换完成状态")
    @PutMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        service.toggle(id);
        return Result.success();
    }

    @Operation(summary = "删除待办")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteTask(id);
        return Result.success();
    }
}
