package com.erp.notification.constant;

/**
 * 通知类型
 */
public enum NotificationType {
    /** 系统消息 */
    SYSTEM("系统"),
    /** 报表摘要(周报/月报) */
    REPORT("报表"),
    /** 管理员公告 */
    ANNOUNCE("公告");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
