package com.erp.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.erp.common.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充通用字段: create_by / create_time / update_by / update_time
 */
@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityUtils.getCurrentUserId();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", Long.class, userId);
        this.strictInsertFill(metaObject, "updateBy", Long.class, userId);
        // 归属业务员: 仅对含 ownerId 字段的实体生效, 已显式赋值则不覆盖
        if (userId != null) {
            this.strictInsertFill(metaObject, "ownerId", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", Long.class, SecurityUtils.getCurrentUserId());
    }
}
