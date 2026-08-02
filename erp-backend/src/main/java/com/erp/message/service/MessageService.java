package com.erp.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.page.PageQuery;
import com.erp.common.security.SecurityUtils;
import com.erp.common.storage.LocalFileStorage;
import com.erp.message.dto.ContactVO;
import com.erp.message.dto.MessageVO;
import com.erp.message.entity.MessageAttachment;
import com.erp.message.entity.UserMessage;
import com.erp.message.mapper.MessageAttachmentMapper;
import com.erp.message.mapper.UserMessageMapper;
import com.erp.system.dto.UserRoleItem;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户间消息服务 - 单聊会话 / 收发件箱 / 附件 / 未读统计。
 */
@Slf4j
@Service
public class MessageService {

    private static final int MESSAGE_RETENTION_DAYS = 14;

    private final UserMessageMapper messageMapper;
    private final MessageAttachmentMapper attachmentMapper;
    private final SysUserMapper userMapper;
    private final LocalFileStorage storage;

    public MessageService(UserMessageMapper messageMapper, MessageAttachmentMapper attachmentMapper,
                          SysUserMapper userMapper, LocalFileStorage storage) {
        this.messageMapper = messageMapper;
        this.attachmentMapper = attachmentMapper;
        this.userMapper = userMapper;
        this.storage = storage;
    }

    /** 发送消息(可带多个附件) */
    @Transactional
    public MessageVO send(Long recipientId, String content, MultipartFile[] files) {
        Long me = currentUserId();
        if (recipientId == null) {
            throw new BusinessException("请选择收件人");
        }
        if (recipientId.equals(me)) {
            throw new BusinessException("不能给自己发消息");
        }
        SysUser recipient = userMapper.selectById(recipientId);
        if (recipient == null || (recipient.getStatus() != null && recipient.getStatus() == 0)) {
            throw new BusinessException("收件人不存在或已停用");
        }
        boolean hasFiles = files != null && files.length > 0;
        if (!StringUtils.hasText(content) && !hasFiles) {
            throw new BusinessException("消息内容不能为空");
        }

        UserMessage m = new UserMessage();
        m.setSenderId(me);
        m.setSenderName(currentUserName());
        m.setRecipientId(recipientId);
        m.setRecipientName(displayName(recipient));
        m.setContent(content);
        m.setIsRead(0);
        m.setSenderDeleted(0);
        m.setRecipientDeleted(0);
        messageMapper.insert(m);

        if (hasFiles) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                LocalFileStorage.Stored s = storage.store(file);
                MessageAttachment a = new MessageAttachment();
                a.setMessageId(m.getId());
                a.setFileName(s.getOriginalName());
                a.setStoredName(s.getStoredName());
                a.setContentType(s.getContentType());
                a.setSize(s.getSize());
                attachmentMapper.insert(a);
            }
        }
        return toVO(List.of(m), me).get(0);
    }

    /** 我和某人的会话(双向, 按时间正序), 同时把对方发我的未读标记为已读 */
    @Transactional
    public List<MessageVO> conversation(Long otherId) {
        Long me = currentUserId();
        LambdaQueryWrapper<UserMessage> w = new LambdaQueryWrapper<UserMessage>()
                .and(q -> q
                        .and(a -> a.eq(UserMessage::getSenderId, me)
                                .eq(UserMessage::getRecipientId, otherId)
                                .eq(UserMessage::getSenderDeleted, 0))
                        .or(o -> o.eq(UserMessage::getSenderId, otherId)
                                .eq(UserMessage::getRecipientId, me)
                                .eq(UserMessage::getRecipientDeleted, 0)))
                .ge(UserMessage::getCreateTime, retentionCutoff())
                .orderByAsc(UserMessage::getCreateTime);
        List<UserMessage> list = messageMapper.selectList(w);

        // 标记对方发我的为已读
        messageMapper.update(null, Wrappers.<UserMessage>lambdaUpdate()
                .set(UserMessage::getIsRead, 1)
                .set(UserMessage::getReadTime, LocalDateTime.now())
                .eq(UserMessage::getSenderId, otherId)
                .eq(UserMessage::getRecipientId, me)
                .eq(UserMessage::getIsRead, 0));

        return toVO(list, me);
    }

    /** 收件箱(分页) */
    public Page<MessageVO> inbox(PageQuery query) {
        Long me = currentUserId();
        Page<UserMessage> page = messageMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getRecipientId, me)
                        .eq(UserMessage::getRecipientDeleted, 0)
                        .ge(UserMessage::getCreateTime, retentionCutoff())
                        .orderByDesc(UserMessage::getCreateTime));
        return mapPage(page, me);
    }

    /** 发件箱(分页) */
    public Page<MessageVO> sent(PageQuery query) {
        Long me = currentUserId();
        Page<UserMessage> page = messageMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<UserMessage>()
                        .eq(UserMessage::getSenderId, me)
                        .eq(UserMessage::getSenderDeleted, 0)
                        .ge(UserMessage::getCreateTime, retentionCutoff())
                        .orderByDesc(UserMessage::getCreateTime));
        return mapPage(page, me);
    }

    /** 联系人列表(其他启用用户 + 各自发给我的未读数) */
    public List<ContactVO> contacts() {
        Long me = currentUserId();
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStatus, 1)
                .ne(SysUser::getId, me)
                .orderByAsc(SysUser::getId));

        // 未读按发件人分组
        List<UserMessage> unread = messageMapper.selectList(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getRecipientId, me)
                .eq(UserMessage::getRecipientDeleted, 0)
                .ge(UserMessage::getCreateTime, retentionCutoff())
                .eq(UserMessage::getIsRead, 0));
        Map<Long, Long> unreadBySender = unread.stream()
                .collect(Collectors.groupingBy(UserMessage::getSenderId, Collectors.counting()));

        List<ContactVO> result = new ArrayList<>();
        for (SysUser u : users) {
            ContactVO c = new ContactVO();
            c.setUserId(u.getId());
            c.setName(displayName(u));
            c.setUsername(u.getUsername());
            c.setRealName(u.getRealName());
            c.setNickname(u.getNickname());
            c.setEmail(u.getEmail());
            c.setPhone(u.getPhone());
            c.setAvatar(u.getAvatar());
            c.setUnread(unreadBySender.getOrDefault(u.getId(), 0L));
            result.add(c);
        }
        attachRoleText(result);
        return result;
    }

    public ContactVO contactDetail(Long userId) {
        if (userId == null || userId.equals(currentUserId())) {
            throw new BusinessException("联系人不存在");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BusinessException("联系人不存在或已停用");
        }
        ContactVO c = new ContactVO();
        c.setUserId(user.getId());
        c.setName(displayName(user));
        c.setUsername(user.getUsername());
        c.setRealName(user.getRealName());
        c.setNickname(user.getNickname());
        c.setEmail(user.getEmail());
        c.setPhone(user.getPhone());
        c.setAvatar(user.getAvatar());
        attachRoleText(List.of(c));
        return c;
    }

    public long unreadTotal() {
        return messageMapper.selectCount(new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getRecipientId, currentUserId())
                .eq(UserMessage::getRecipientDeleted, 0)
                .ge(UserMessage::getCreateTime, retentionCutoff())
                .eq(UserMessage::getIsRead, 0));
    }

    public void markRead(Long id) {
        messageMapper.update(null, Wrappers.<UserMessage>lambdaUpdate()
                .set(UserMessage::getIsRead, 1)
                .set(UserMessage::getReadTime, LocalDateTime.now())
                .eq(UserMessage::getId, id)
                .eq(UserMessage::getRecipientId, currentUserId()));
    }

    /** 从我这一侧删除某条消息(不影响对方) */
    public void deleteForMe(Long id) {
        Long me = currentUserId();
        UserMessage m = messageMapper.selectById(id);
        if (m == null) {
            throw new BusinessException("消息不存在");
        }
        if (me.equals(m.getSenderId())) {
            messageMapper.update(null, Wrappers.<UserMessage>lambdaUpdate()
                    .set(UserMessage::getSenderDeleted, 1).eq(UserMessage::getId, id));
        } else if (me.equals(m.getRecipientId())) {
            messageMapper.update(null, Wrappers.<UserMessage>lambdaUpdate()
                    .set(UserMessage::getRecipientDeleted, 1).eq(UserMessage::getId, id));
        } else {
            throw new BusinessException("无权删除该消息");
        }
    }

    /** 清空当前用户与某联系人的可见聊天记录, 不影响对方账号的会话视图。 */
    @Transactional
    public void clearConversationForMe(Long otherId) {
        Long me = currentUserId();
        if (otherId == null || otherId.equals(me)) {
            throw new BusinessException("联系人不存在");
        }
        SysUser other = userMapper.selectById(otherId);
        if (other == null || (other.getStatus() != null && other.getStatus() == 0)) {
            throw new BusinessException("联系人不存在或已停用");
        }
        messageMapper.update(null, Wrappers.<UserMessage>lambdaUpdate()
                .set(UserMessage::getSenderDeleted, 1)
                .eq(UserMessage::getSenderId, me)
                .eq(UserMessage::getRecipientId, otherId)
                .eq(UserMessage::getSenderDeleted, 0));
        messageMapper.update(null, Wrappers.<UserMessage>lambdaUpdate()
                .set(UserMessage::getRecipientDeleted, 1)
                .set(UserMessage::getIsRead, 1)
                .set(UserMessage::getReadTime, LocalDateTime.now())
                .eq(UserMessage::getSenderId, otherId)
                .eq(UserMessage::getRecipientId, me)
                .eq(UserMessage::getRecipientDeleted, 0));
    }

    /** 取出可访问的附件(必须是消息收发双方之一) */
    public MessageAttachment accessibleAttachment(Long attachmentId) {
        Long me = currentUserId();
        MessageAttachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BusinessException("附件不存在");
        }
        UserMessage m = messageMapper.selectById(a.getMessageId());
        if (m == null || (!me.equals(m.getSenderId()) && !me.equals(m.getRecipientId()))) {
            throw new BusinessException("无权下载该附件");
        }
        return a;
    }

    public java.nio.file.Path resolve(MessageAttachment a) {
        return storage.resolve(a.getStoredName());
    }

    @Scheduled(cron = "${erp.message.cleanup-cron:0 15 3 * * ?}")
    public void cleanupExpiredMessages() {
        LocalDateTime cutoff = retentionCutoff();
        List<UserMessage> expired = messageMapper.selectList(new LambdaQueryWrapper<UserMessage>()
                .lt(UserMessage::getCreateTime, cutoff));
        if (expired.isEmpty()) {
            return;
        }
        List<Long> ids = expired.stream().map(UserMessage::getId).toList();
        List<MessageAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<MessageAttachment>().in(MessageAttachment::getMessageId, ids));
        for (MessageAttachment attachment : attachments) {
            storage.delete(attachment.getStoredName());
        }
        attachmentMapper.delete(new LambdaQueryWrapper<MessageAttachment>().in(MessageAttachment::getMessageId, ids));
        messageMapper.deleteBatchIds(ids);
        log.info("[MESSAGE] cleaned {} expired messages older than {} days", expired.size(), MESSAGE_RETENTION_DAYS);
    }

    // ---------------- 私有辅助 ----------------

    private Page<MessageVO> mapPage(Page<UserMessage> page, Long me) {
        Page<MessageVO> vo = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        vo.setRecords(toVO(page.getRecords(), me));
        return vo;
    }

    private List<MessageVO> toVO(List<UserMessage> list, Long me) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = list.stream().map(UserMessage::getId).collect(Collectors.toList());
        Map<Long, List<MessageAttachment>> attMap = attachmentMapper.selectList(
                        new LambdaQueryWrapper<MessageAttachment>().in(MessageAttachment::getMessageId, ids))
                .stream().collect(Collectors.groupingBy(MessageAttachment::getMessageId));

        return list.stream().map(m -> {
            MessageVO v = new MessageVO();
            v.setId(m.getId());
            v.setSenderId(m.getSenderId());
            v.setSenderName(m.getSenderName());
            v.setRecipientId(m.getRecipientId());
            v.setRecipientName(m.getRecipientName());
            v.setContent(m.getContent());
            v.setIsRead(m.getIsRead());
            v.setCreateTime(m.getCreateTime());
            v.setMine(me.equals(m.getSenderId()));
            v.setAttachments(attMap.getOrDefault(m.getId(), Collections.emptyList()).stream().map(a -> {
                MessageVO.AttachmentVO av = new MessageVO.AttachmentVO();
                av.setId(a.getId());
                av.setFileName(a.getFileName());
                av.setContentType(a.getContentType());
                av.setSize(a.getSize());
                return av;
            }).collect(Collectors.toList()));
            return v;
        }).collect(Collectors.toList());
    }

    private String displayName(SysUser u) {
        if (u == null) {
            return "用户";
        }
        if (StringUtils.hasText(u.getNickname())) {
            return u.getNickname();
        }
        return StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername();
    }

    private String currentUserName() {
        var u = SecurityUtils.getLoginUser();
        if (u == null) {
            return "系统";
        }
        String nick = u.getUser().getNickname();
        if (StringUtils.hasText(nick)) {
            return nick;
        }
        String real = u.getUser().getRealName();
        return StringUtils.hasText(real) ? real : u.getUsername();
    }

    private void attachRoleText(List<ContactVO> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return;
        }
        List<Long> ids = contacts.stream().map(ContactVO::getUserId).toList();
        Map<Long, String> byUser = userMapper.selectRolesByUserIds(ids).stream()
                .collect(Collectors.groupingBy(UserRoleItem::getUserId,
                        Collectors.mapping(UserRoleItem::getRoleName, Collectors.joining(" / "))));
        contacts.forEach(c -> c.setRoles(byUser.get(c.getUserId())));
    }

    private LocalDateTime retentionCutoff() {
        return LocalDateTime.now().minusDays(MESSAGE_RETENTION_DAYS);
    }

    private Long currentUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("未登录");
        }
        return uid;
    }
}
