package com.ihdrs.backend.aspect;

import com.ihdrs.backend.annotation.LogOperation;
import com.ihdrs.backend.entity.UserLog;
import com.ihdrs.backend.repository.UserLogRepository;
import com.ihdrs.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 用户操作日志切面
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LogAspect {

    private final UserLogRepository userLogRepository;
    private final AuthService authService;

    /**
     * 环绕通知，记录用户操作日志
     */
    @Around("@annotation(com.ihdrs.backend.annotation.LogOperation)")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();

        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解信息
        LogOperation logOperation = method.getAnnotation(LogOperation.class);

        // 执行目标方法
        Object result = joinPoint.proceed();

        // 异步记录日志（不影响主流程）
        try {
            recordLog(request, logOperation, joinPoint);
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }

        return result;
    }

    /**
     * 记录日志到数据库
     */
    private void recordLog(HttpServletRequest request, LogOperation logOperation,
                           ProceedingJoinPoint joinPoint) {
        try {
            // 获取用户ID
            Long userId = getUserIdFromToken(request);
            if (userId == null) {
                return; // 未登录的请求不记录
            }

            // 获取操作描述
            String action = buildActionDescription(logOperation, joinPoint);

            // 获取IP地址
            String ipAddress = getClientIp(request);

            // 获取UserAgent
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 500) {
                userAgent = userAgent.substring(0, 500);
            }

            // 创建日志对象
            UserLog userLog = new UserLog();
            userLog.setUserId(userId);
            userLog.setAction(action);
            userLog.setIpAddress(ipAddress);
            userLog.setUserAgent(userAgent);

            // 保存到数据库
            userLogRepository.save(userLog);

            log.info("记录用户操作日志: userId={}, action={}, ip={}", userId, action, ipAddress);

        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }

    /**
     * 从 Token 中获取用户ID
     */
    private Long getUserIdFromToken(HttpServletRequest request) {
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring(7);
                return authService.validateToken(token).getData().getUserId();
            }
        } catch (Exception e) {
            log.warn("获取用户ID失败", e);
        }
        return null;
    }

    /**
     * 构建操作描述
     */
    private String buildActionDescription(LogOperation logOperation, ProceedingJoinPoint joinPoint) {
        StringBuilder action = new StringBuilder();

        // 操作类型
        action.append("[").append(logOperation.type().getDescription()).append("] ");

        // 操作描述
        if (logOperation.value() != null && !logOperation.value().isEmpty()) {
            action.append(logOperation.value());
        } else {
            // 如果没有描述，使用方法名
            action.append(joinPoint.getSignature().getName());
        }

        // 可选：添加参数信息
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            action.append(" - 参数: ");
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !(args[i] instanceof HttpServletRequest)) {
                    action.append(args[i].getClass().getSimpleName());
                    if (i < args.length - 1) {
                        action.append(", ");
                    }
                }
            }
        }

        return action.toString();
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多个IP的情况，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}