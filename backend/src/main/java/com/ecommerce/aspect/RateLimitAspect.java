package com.ecommerce.aspect;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.RateLimit;
import com.ecommerce.utils.RedisUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * API 限流切面
 * 基于 Redis 实现滑动窗口限流
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    @Autowired
    private RedisUtil redisUtil;

    @Around("@annotation(com.ecommerce.common.RateLimit)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String key = buildKey(rateLimit, method);
        if (key == null) {
            return point.proceed();
        }

        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long currentCount = incrementCount(redisKey, rateLimit.window());

        if (currentCount > rateLimit.max()) {
            log.warn("[限流] key={}, count={}, max={}", key, currentCount, rateLimit.max());
            throw BusinessException.tooManyRequests(rateLimit.message());
        }

        log.debug("[限流检查] key={}, count={}, max={}", key, currentCount, rateLimit.max());
        return point.proceed();
    }

    /**
     * 构建限流键
     */
    private String buildKey(RateLimit rateLimit, Method method) {
        String prefix = rateLimit.key().isEmpty() 
                ? method.getDeclaringClass().getSimpleName() + ":" + method.getName()
                : rateLimit.key();

        String suffix = "";
        switch (rateLimit.type()) {
            case IP:
                suffix = ":ip:" + getClientIp();
                break;
            case USER:
                Long userId = getCurrentUserId();
                if (userId != null) {
                    suffix = ":user:" + userId;
                    break;
                }
                suffix = ":ip:" + getClientIp();
                break;
            case ALL:
                suffix = ":all";
                break;
        }

        return prefix + suffix;
    }

    /**
     * 递增计数并设置过期时间
     */
    private long incrementCount(String key, int windowSeconds) {
        Long count = redisUtil.incr(key, 1);
        if (count != null && count == 1) {
            redisUtil.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count != null ? count : 0;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        Object userId = request.getAttribute("userId");
        return userId instanceof Long ? (Long) userId : null;
    }
}
