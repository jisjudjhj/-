package com.ecommerce.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret:}")
    private String configuredSecret;

    @Value("${jwt.allow-ephemeral-secret:false}")
    private boolean allowEphemeralSecret;

    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @PostConstruct
    public void initSecret() {
        if (configuredSecret == null || configuredSecret.trim().isEmpty()) {
            if (!allowEphemeralSecret) {
                log.error("[JWT] Missing required config: jwt.secret. Refusing to start with ephemeral secret.");
                throw new IllegalStateException("JWT secret is required. Please set `jwt.secret` or `JWT_SECRET`.");
            }
            secret = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
            log.warn("[JWT] jwt.secret is empty and jwt.allow-ephemeral-secret=true. Generated an ephemeral in-memory secret for this process.");
            return;
        }
        secret = configuredSecret.trim();
    }

    public String generateToken(Long userId, String username, String role) {
        return generateToken(userId, username, role, 0);
    }

    public String generateToken(Long userId, String username, String role, int tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("tokenVersion", tokenVersion);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.get("userId").toString());
    }

    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    public String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get("role").toString();
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
