package com.erp.notification.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageResult;
import com.erp.notification.constant.NotificationType;
import com.erp.notification.dto.BroadcastRequest;
import com.erp.notification.dto.NotificationQuery;
import com.erp.notification.dto.RecipientVO;
import com.erp.notification.entity.SysNotification;
import com.erp.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 站内通知 - 当前用户的消息中心 + 管理员公告广播
 */
@Tag(name = "站内通知")
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "我的通知分页")
    @GetMapping("/page")
    public Result<PageResult<SysNotification>> page(NotificationQuery query) {
        return Result.success(PageResult.of(notificationService.pageForCurrentUser(query)));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("count", notificationService.unreadCount()));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.success();
    }

    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead();
        return Result.success();
    }

    @Operation(summary = "选择已读 (批量)")
    @PutMapping("/read-batch")
    public Result<Void> markReadBatch(@RequestBody List<Long> ids) {
        notificationService.markReadBatch(ids);
        return Result.success();
    }

    @Operation(summary = "删除通知 (仅管理员; 公告整条撤回)")
    @OperationLog(module = "站内通知", operation = "删除通知")
    @PreAuthorize("@perm.has('system:notification:send')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return Result.success();
    }

    @Operation(summary = "公告可选接收人 (管理员)")
    @PreAuthorize("@perm.has('system:notification:send')")
    @GetMapping("/recipients")
    public Result<List<RecipientVO>> recipients() {
        return Result.success(notificationService.listRecipients());
    }

    @Operation(summary = "发送公告 (指定接收人或全部启用用户)")
    @OperationLog(module = "站内通知", operation = "发送公告")
    @PreAuthorize("@perm.has('system:notification:send')")
    @PostMapping("/broadcast")
    public Result<Map<String, Integer>> broadcast(@Valid @RequestBody BroadcastRequest req) {
        int n = notificationService.broadcast(req.getTitle(), req.getContent(),
                NotificationType.ANNOUNCE, req.isSendEmail(), req.getRecipientIds());
        return Result.success("已发送", Map.of("recipients", n));
    }
}
