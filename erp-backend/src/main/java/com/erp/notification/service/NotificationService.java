package com.erp.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.mail.MailService;
import com.erp.common.security.SecurityUtils;
import com.erp.notification.constant.NotificationType;
import com.erp.notification.entity.SysNotification;
import com.erp.notification.mapper.SysNotificationMapper;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 站内通知服务 - 单发 / 广播 / 当前用户的查询与已读管理。
 */
@Slf4j
@Service
public class NotificationService {

    private final SysNotificationMapper notificationMapper;
    private final SysUserMapper userMapper;
    private final MailService mailService;

    public NotificationService(SysNotificationMapper notificationMapper, SysUserMapper userMapper,
                               MailService mailService) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.mailService = mailService;
    }

    /** 给单个用户发送站内通知 */
    public void createForUser(Long userId, String title, String content, NotificationType type) {
        createForUser(userId, title, content, type, null);
    }

    /** 给单个用户发送站内通知(可带分组键, 用于广播整条删除) */
    public void createForUser(Long userId, String title, String content, NotificationType type, String groupKey) {
        SysNotification n = new SysNotification();
        n.setRecipientId(userId);
        n.setTitle(title);
        n.setContent(content);
        n.setType(type.name());
        n.setGroupKey(groupKey);
        n.setIsRead(0);
        n.setCreateBy(SecurityUtils.getCurrentUserId());
        n.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    /** Send a notification to every enabled user under the given role. */
    public int createForRole(String roleKey, String title, String content, NotificationType type, String groupKey) {
        if (!StringUtils.hasText(roleKey)) {
            return 0;
        }
        List<SysUser> users = userMapper.selectActiveUsersByRoleKey(roleKey);
        int count = 0;
        for (SysUser user : users) {
            createForUser(user.getId(), title, content, type, groupKey);
            count++;
        }
        return count;
    }

    /** Send a notification to every enabled user under any of the given roles, de-duplicated by user id. */
    public int createForRoles(Collection<String> roleKeys, String title, String content,
                              NotificationType type, String groupKey) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return 0;
        }
        List<String> keys = roleKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return 0;
        }
        List<SysUser> users = userMapper.selectActiveUsersByRoleKeys(keys);
        int count = 0;
        for (SysUser user : users) {
            createForUser(user.getId(), title, content, type, groupKey);
            count++;
        }
        return count;
    }

    /**
     * 按业务分组键向角色发送一次性待办通知。同一接收人已存在同一分组键时不重复创建。
     */
    public int createForRolesOnce(Collection<String> roleKeys, String title, String content,
                                  NotificationType type, String groupKey) {
        if (!StringUtils.hasText(groupKey)) {
            return createForRoles(roleKeys, title, content, type, null);
        }
        if (roleKeys == null || roleKeys.isEmpty()) {
            return 0;
        }
        List<String> keys = roleKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (SysUser user : userMapper.selectActiveUsersByRoleKeys(keys)) {
            Long exists = notificationMapper.selectCount(Wrappers.<SysNotification>lambdaQuery()
                    .eq(SysNotification::getRecipientId, user.getId())
                    .eq(SysNotification::getGroupKey, groupKey));
            if (exists != null && exists > 0) {
                continue;
            }
            createForUser(user.getId(), title, content, type, groupKey);
            count++;
        }
        return count;
    }

    /** 广播给全部启用用户 (兼容旧签名) */
    public int broadcast(String title, String content, NotificationType type, boolean sendEmail) {
        return broadcast(title, content, type, sendEmail, null);
    }

    /** Broadcast to enabled users under any of the supplied roles, de-duplicated by user id. */
    public int broadcastToRoles(Collection<String> roleKeys, String title, String content,
                                NotificationType type, boolean sendEmail) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            throw new BusinessException("接收角色不能为空");
        }
        List<String> keys = roleKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            throw new BusinessException("接收角色不能为空");
        }
        List<Long> recipientIds = userMapper.selectActiveUsersByRoleKeys(keys).stream()
                .map(SysUser::getId)
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            throw new BusinessException("没有符合角色范围的接收用户");
        }
        return broadcast(title, content, type, sendEmail, recipientIds);
    }

    /**
     * 发送公告: recipientIds 为空=全部启用用户, 否则只发给指定(且启用)的用户; 可选同时发邮件。
     *
     * @return 接收人数
     */
    public int broadcast(String title, String content, NotificationType type, boolean sendEmail,
                         List<Long> recipientIds) {
        var wrapper = Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStatus, 1);
        if (recipientIds != null && !recipientIds.isEmpty()) {
            wrapper.in(SysUser::getId, recipientIds);
        }
        List<SysUser> users = userMapper.selectList(wrapper);
        if (users.isEmpty()) {
            throw new BusinessException("没有可接收的用户");
        }
        String groupKey = java.util.UUID.randomUUID().toString().replace("-", "");
        for (SysUser u : users) {
            createForUser(u.getId(), title, content, type, groupKey);
        }
        if (sendEmail) {
            int mailed = 0;
            for (SysUser u : users) {
                if (StringUtils.hasText(u.getEmail())) {
                    mailService.sendHtml(u.getEmail(), title, content);
                    mailed++;
                }
            }
            log.info("[NOTIFY] 广播 \"{}\" 站内 {} 人, 邮件 {} 人", title, users.size(), mailed);
        } else {
            log.info("[NOTIFY] 广播 \"{}\" 站内 {} 人", title, users.size());
        }
        return users.size();
    }

    public Page<SysNotification> pageForCurrentUser(com.erp.notification.dto.NotificationQuery query) {
        Long userId = currentUserId();
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getRecipientId, userId)
                .eq(Boolean.TRUE.equals(query.getOnlyUnread()), SysNotification::getIsRead, 0)
                .eq(StringUtils.hasText(query.getType()), SysNotification::getType, query.getType())
                .orderByDesc(SysNotification::getCreateTime);
        return notificationMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    public long unreadCount() {
        return notificationMapper.selectCount(Wrappers.<SysNotification>lambdaQuery()
                .eq(SysNotification::getRecipientId, currentUserId())
                .eq(SysNotification::getIsRead, 0));
    }

    public void markRead(Long id) {
        update(new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getId, id)
                .eq(SysNotification::getRecipientId, currentUserId()));
    }

    public void markAllRead() {
        update(new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getRecipientId, currentUserId())
                .eq(SysNotification::getIsRead, 0));
    }

    /** 将选中的若干通知标记为已读(仅限当前用户自己的) */
    public void markReadBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        update(new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getRecipientId, currentUserId())
                .in(SysNotification::getId, ids)
                .eq(SysNotification::getIsRead, 0));
    }

    /** 公告可选接收人(全部启用用户), 供管理员发送公告时挑选 */
    public List<com.erp.notification.dto.RecipientVO> listRecipients() {
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getId))
                .stream()
                .map(u -> new com.erp.notification.dto.RecipientVO(u.getId(),
                        StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 删除通知:
     * - 公告(ANNOUNCE): 仅管理员可删, 且整条删除(按 group_key 删全部副本; 旧数据无 group_key 则删本条)。
     * - 其它(系统/报表等个人通知): 删除当前用户自己的那一条。
     */
    public void delete(Long id) {
        Long me = currentUserId();
        SysNotification n = notificationMapper.selectOne(Wrappers.<SysNotification>lambdaQuery()
                .eq(SysNotification::getId, id)
                .eq(SysNotification::getRecipientId, me));
        if (n == null) {
            throw new BusinessException("通知不存在");
        }
        if (NotificationType.ANNOUNCE.name().equals(n.getType())) {
            if (!isAdmin()) {
                throw new BusinessException("只有管理员可删除公告");
            }
            if (StringUtils.hasText(n.getGroupKey())) {
                notificationMapper.delete(Wrappers.<SysNotification>lambdaQuery()
                        .eq(SysNotification::getGroupKey, n.getGroupKey()));
            } else {
                notificationMapper.deleteById(id);
            }
        } else {
            notificationMapper.delete(Wrappers.<SysNotification>lambdaQuery()
                    .eq(SysNotification::getId, id)
                    .eq(SysNotification::getRecipientId, me));
        }
    }

    /** 当前用户是否为超级管理员(持通配权限 *) */
    private boolean isAdmin() {
        var u = SecurityUtils.getLoginUser();
        return u != null && u.getPermissions() != null && u.getPermissions().contains("*");
    }

    private void update(LambdaUpdateWrapper<SysNotification> wrapper) {
        wrapper.set(SysNotification::getIsRead, 1).set(SysNotification::getReadTime, LocalDateTime.now());
        notificationMapper.update(null, wrapper);
    }

    private Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return userId;
    }
}
