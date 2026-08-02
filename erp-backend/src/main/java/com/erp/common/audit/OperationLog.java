package com.erp.common.audit;

import java.lang.annotation.*;

/**
 * 操作审计注解 - 标注在 Controller 方法上, 由 {@link OperationLogAspect} 记录
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 所属模块, 如: 客户资料 */
    String module() default "";

    /** 操作描述, 如: 新建客户 */
    String operation() default "";
}
