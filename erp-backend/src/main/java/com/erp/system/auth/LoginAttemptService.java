package com.erp.system.auth;

import com.erp.common.security.SecurityProperties;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 登录失败锁定 - 连续失败 N 次后锁定 M 分钟, 防暴力破解。
 * 复用可插拔的 {@link VerificationCodeStore} 作为带 TTL 的计数器存储 (Redis/内存)。
 */
@Service
public class LoginAttemptService {

    private static final String KEY_PREFIX = "login:fail:";

    private final VerificationCodeStore store;
    private final SecurityProperties props;

    public LoginAttemptService(VerificationCodeStore store, SecurityProperties props) {
        this.store = store;
        this.props = props;
    }

    public boolean isLocked(String username) {
        return current(username) >= props.getLogin().getMaxAttempts();
    }

    public int getLockMinutes() {
        return props.getLogin().getLockMinutes();
    }

    /** 记录一次失败, 返回剩余可尝试次数(0 表示已锁定) */
    public int recordFailure(String username) {
        int count = current(username) + 1;
        store.set(key(username), String.valueOf(count), props.getLogin().getLockMinutes() * 60L);
        return Math.max(0, props.getLogin().getMaxAttempts() - count);
    }

    /** 登录成功后清除计数 */
    public void reset(String username) {
        store.remove(key(username));
    }

    private int current(String username) {
        String v = store.get(key(username));
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String key(String username) {
        return KEY_PREFIX + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }
}
