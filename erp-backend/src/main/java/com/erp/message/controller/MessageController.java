package com.erp.message.controller;

import com.erp.common.api.Result;
import com.erp.common.audit.OperationLog;
import com.erp.common.page.PageQuery;
import com.erp.common.page.PageResult;
import com.erp.message.dto.ContactVO;
import com.erp.message.dto.MessageVO;
import com.erp.message.entity.MessageAttachment;
import com.erp.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 用户间消息 - 联系人 / 单聊会话 / 收发件箱 / 附件下载 (类似邮件 + 企业微信单聊)
 */
@Tag(name = "用户消息")
@RestController
@RequestMapping("/message")
@PreAuthorize("@perm.has('message:list')")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @Operation(summary = "联系人列表(含未读数)")
    @GetMapping("/contacts")
    public Result<List<ContactVO>> contacts() {
        return Result.success(service.contacts());
    }

    @Operation(summary = "联系人资料")
    @GetMapping("/contacts/{userId}")
    public Result<ContactVO> contactDetail(@PathVariable Long userId) {
        return Result.success(service.contactDetail(userId));
    }

    @Operation(summary = "与某用户的会话")
    @GetMapping("/conversation/{userId}")
    public Result<List<MessageVO>> conversation(@PathVariable Long userId) {
        return Result.success(service.conversation(userId));
    }

    @Operation(summary = "收件箱")
    @GetMapping("/inbox")
    public Result<PageResult<MessageVO>> inbox(PageQuery query) {
        return Result.success(PageResult.of(service.inbox(query)));
    }

    @Operation(summary = "发件箱")
    @GetMapping("/sent")
    public Result<PageResult<MessageVO>> sent(PageQuery query) {
        return Result.success(PageResult.of(service.sent(query)));
    }

    @Operation(summary = "未读总数")
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("count", service.unreadTotal()));
    }

    @Operation(summary = "发送消息(可带附件)")
    @OperationLog(module = "用户消息", operation = "发送消息")
    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MessageVO> send(@RequestParam Long recipientId,
                                  @RequestParam(required = false) String content,
                                  @RequestParam(required = false) MultipartFile[] files) {
        return Result.success("已发送", service.send(recipientId, content, files));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        service.markRead(id);
        return Result.success();
    }

    @Operation(summary = "从我这一侧删除消息")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteForMe(id);
        return Result.success();
    }

    @Operation(summary = "清空与某联系人的聊天记录(仅当前用户视图)")
    @DeleteMapping("/conversation/{userId}")
    public Result<Void> clearConversation(@PathVariable Long userId) {
        service.clearConversationForMe(userId);
        return Result.success();
    }

    @Operation(summary = "下载消息附件")
    @GetMapping("/attachment/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        MessageAttachment a = service.accessibleAttachment(id);
        Path path = service.resolve(a);
        if (!Files.exists(path)) {
            throw new com.erp.common.exception.BusinessException("文件已丢失");
        }
        Resource resource = new FileSystemResource(path);
        String fileName = URLEncoder.encode(a.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + fileName)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
