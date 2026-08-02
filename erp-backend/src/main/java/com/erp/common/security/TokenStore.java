package com.erp.common.security;

/**
 * 登录态存储策略 - 默认 Redis(集群/多实例), 本地开发可切换为内存实现。
 * 由配置 erp.session.store=redis|memory 选择具体实现。
 */
public interface TokenStore {

    /** 缓存登录态 */
    void set(String token, LoginUser loginUser, long ttlMinutes);

    /** 取登录态, 不存在或已过期返回 null */
    LoginUser get(String token);

    /** 移除登录态 (注销) */
    void remove(String token);

    /** 移除某个用户的全部登录态 (权限、状态或密码变更后强制重新登录) */
    void removeByUserId(Long userId);

    /** Remove every active login session after a system-wide reset. */
    void removeAll();
}
