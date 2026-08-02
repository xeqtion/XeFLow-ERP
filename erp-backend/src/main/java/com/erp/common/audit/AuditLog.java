package com.erp.common.audit;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志 - 谁 / 何时 / 对什么 / 做了什么
 */
@Data
@TableName("audit_log")
public class AuditLog implements Serializable {

    @TableId
    private Long id;

    private Long userId;
    private String username;

    /** 模块 */
    private String module;

    /** 操作 */
    private String operation;

    /** 目标方法 */
    private String method;

    private String requestUri;
    private String requestMethod;

    /** 请求参数(JSON, 截断) */
    private String params;

    /** 是否成功 */
    private Integer success;

    /** 异常信息 */
    private String errorMsg;

    /** 客户端 IP */
    private String ip;

    /** 耗时(毫秒) */
    private Long costMs;

    private LocalDateTime createTime;
}
