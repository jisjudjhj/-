package com.ecommerce.filter;

import com.ecommerce.common.Constants;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.utils.JwtUtil;
import com.ecommerce.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * JWT 认证过滤器，嵌入 Spring Security 过滤链。
 *
 * 优化：
 * 1. 用户角色/状态缓存到 Redis（TTL 5分钟），避免每次请求查 DB
 * 2. 支持 Token 版本号校验 — 修改密码/禁用账户后旧 Token 立即失效
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTH_CACHE_PREFIX = "auth:user:";
    private static final int AUTH_CACHE_MINUTES = 5;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(Constants.TOKEN_HEADER);
        if (header != null && header.startsWith(Constants.TOKEN_PREFIX)) {
            String token = header.substring(Constants.TOKEN_PREFIX.length());
            try {
                Claims claims = jwtUtil.parseToken(token);
                if (claims.getExpiration() != null && !claims.getExpiration().before(new java.util.Date())) {
                    Long userId = Long.valueOf(claims.get("userId").toString());

                    Object tokenVersionClaim = claims.get("tokenVersion");
                    int claimVersion = tokenVersionClaim != null
                            ? Integer.parseInt(tokenVersionClaim.toString()) : 0;

                    CachedUserAuth cachedAuth = loadUserAuth(userId);
                    if (cachedAuth != null && cachedAuth.status != 0) {
                        if (cachedAuth.tokenVersion > claimVersion) {
                            log.debug("[JWT] Token版本过期 userId={}, claim={}, current={}",
                                    userId, claimVersion, cachedAuth.tokenVersion);
                            filterChain.doFilter(request, response);
                            return;
                        }

                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                                "ROLE_" + cachedAuth.role.toUpperCase());
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userId, null, Collections.singletonList(authority));
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        request.setAttribute("userId", userId);
                        request.setAttribute("username", claims.getSubject());
                        request.setAttribute("role", cachedAuth.role);
                    }
                }
            } catch (Exception e) {
                log.debug("[JWT] Token验证失败: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private CachedUserAuth loadUserAuth(Long userId) {
        String cacheKey = AUTH_CACHE_PREFIX + userId;
        try {
            Object cached = redisUtil.get(cacheKey);
            if (cached != null) {
                String[] parts = cached.toString().split(":");
                if (parts.length >= 3) {
                    CachedUserAuth auth = new CachedUserAuth();
                    auth.role = parts[0];
                    auth.status = Integer.parseInt(parts[1]);
                    auth.tokenVersion = Integer.parseInt(parts[2]);
                    return auth;
                }
            }
        } catch (Exception e) {
            log.debug("[JWT] 认证缓存读取失败: {}", e.getMessage());
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        CachedUserAuth auth = new CachedUserAuth();
        auth.role = user.getRole();
        auth.status = user.getStatus() != null ? user.getStatus() : 1;
        auth.tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;

        try {
            String cacheValue = auth.role + ":" + auth.status + ":" + auth.tokenVersion;
            redisUtil.set(cacheKey, cacheValue, AUTH_CACHE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("[JWT] 认证缓存写入失败: {}", e.getMessage());
        }

        return auth;
    }

    /**
     * 清除用户认证缓存（供修改密码、禁用账户等场景调用）
     */
    public void evictAuthCache(Long userId) {
        try {
            redisUtil.delete(AUTH_CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("[JWT] 清除认证缓存失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private static class CachedUserAuth {
        String role;
        int status;
        int tokenVersion;
    }
}
