package com.erp.common.audit;

import com.erp.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审计日志切面 - 拦截 @OperationLog 方法并落库
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private static final int MAX_PARAM_LEN = 2000;
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "authorization",
            "code",
            "newpassword",
            "oldpassword",
            "password",
            "registercode",
            "secret",
            "token"
    );

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OperationLogAspect(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        AuditLog entry = new AuditLog();
        entry.setModule(operationLog.module());
        entry.setOperation(operationLog.operation());
        entry.setMethod(((MethodSignature) pjp.getSignature()).getMethod().getName());
        entry.setUserId(SecurityUtils.getCurrentUserId());
        entry.setUsername(SecurityUtils.getCurrentUsername());
        fillRequest(entry);
        entry.setParams(toJson(pjp.getArgs()));
        try {
            Object result = pjp.proceed();
            entry.setSuccess(1);
            return result;
        } catch (Throwable ex) {
            entry.setSuccess(0);
            entry.setErrorMsg(truncate(ex.getMessage(), 500));
            throw ex;
        } finally {
            entry.setCostMs(System.currentTimeMillis() - start);
            entry.setCreateTime(LocalDateTime.now());
            save(entry);
        }
    }

    private void save(AuditLog entry) {
        try {
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }

    private void fillRequest(AuditLog entry) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            entry.setRequestUri(request.getRequestURI());
            entry.setRequestMethod(request.getMethod());
            entry.setIp(resolveIp(request));
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }

    private String toJson(Object[] args) {
        try {
            JsonNode node = objectMapper.valueToTree(sanitizeArgs(args));
            mask(node);
            return truncate(objectMapper.writeValueAsString(node), MAX_PARAM_LEN);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Object> sanitizeArgs(Object[] args) {
        if (args == null) {
            return List.of();
        }
        return java.util.Arrays.stream(args).map(this::sanitizeArg).toList();
    }

    private Object sanitizeArg(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof MultipartFile file) {
            Map<String, Object> info = new java.util.LinkedHashMap<>();
            info.put("fileName", file.getOriginalFilename());
            info.put("size", file.getSize());
            info.put("contentType", file.getContentType());
            return info;
        }
        if (arg instanceof MultipartFile[] files) {
            return java.util.Arrays.stream(files).map(this::sanitizeArg).toList();
        }
        if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
            return arg.getClass().getSimpleName();
        }
        return arg;
    }

    private void mask(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    objectNode.put(field.getKey(), "******");
                } else {
                    mask(field.getValue());
                }
            }
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::mask);
        }
    }

    private boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
        return SENSITIVE_FIELDS.contains(normalized)
                || normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("authorization");
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
