package com.erp.system.auth;

/**
 * 验证码存储策略 - 与登录态存储一致, 默认 Redis, 本地可切内存。
 * 由配置 erp.session.store=redis|memory 选择具体实现。
 */
public interface VerificationCodeStore {

    /** 写入验证码 (覆盖旧值), 指定有效期秒数 */
    void set(String key, String code, long ttlSeconds);

    /** 读取验证码, 不存在或已过期返回 null */
    String get(String key);

    /** 删除验证码 (验证通过后调用) */
    void remove(String key);
}
