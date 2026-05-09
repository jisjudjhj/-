package com.ecommerce.filter;

import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 注册接口前置限流。
 *
 * 放在 Controller、参数校验、验证码校验之前，先挡住批量注册洪峰。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RegisterRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RegisterRateLimitFilter.class);
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String RATE_LIMIT_KEY_PREFIX = "edge_rate_limit:auth:register:ip:";

    @Autowired
    private RedisUtil redisUtil;

    @Value("${security.register-rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${security.register-rate-limit.max:10}")
    private int maxRequests;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !REGISTER_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        long currentCount = incrementCount(key);

        if (currentCount > maxRequests) {
            log.warn("[注册前置限流] ip={}, count={}, max={}, window={}s",
                    clientIp, currentCount, maxRequests, windowSeconds);
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private long incrementCount(String key) {
        Long count = redisUtil.incr(key, 1);
        if (count != null && count == 1) {
            redisUtil.expire(key, Math.max(1, windowSeconds), TimeUnit.SECONDS);
        }
        return count != null ? count : 0L;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int commaIndex = ip.indexOf(',');
            return commaIndex >= 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        byte[] body = "{\"code\":429,\"message\":\"注册请求频繁，请稍后再试\"}"
                .getBytes(StandardCharsets.UTF_8);
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
