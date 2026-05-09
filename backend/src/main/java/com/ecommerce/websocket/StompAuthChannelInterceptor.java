package com.ecommerce.websocket;

import com.ecommerce.common.Constants;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String header = resolveAuthorizationHeader(accessor);
        if (!StringUtils.hasText(header) || !header.startsWith(Constants.TOKEN_PREFIX)) {
            throw new MessagingException("WebSocket authentication failed: missing token");
        }

        String token = header.substring(Constants.TOKEN_PREFIX.length()).trim();
        try {
            Claims claims = jwtUtil.parseToken(token);
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                throw new MessagingException("WebSocket authentication failed: token expired");
            }

            Long userId = parseLong(claims.get("userId"));
            if (userId == null || userId <= 0) {
                throw new MessagingException("WebSocket authentication failed: invalid user");
            }

            User user = userMapper.selectById(userId);
            if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
                throw new MessagingException("WebSocket authentication failed: account disabled");
            }

            int claimTokenVersion = parseInteger(claims.get("tokenVersion"), 0);
            int currentTokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
            if (currentTokenVersion > claimTokenVersion) {
                throw new MessagingException("WebSocket authentication failed: token version expired");
            }

            String role = StringUtils.hasText(user.getRole()) ? user.getRole() : Constants.Role.USER;
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    String.valueOf(userId),
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
            accessor.setUser(authentication);
            return message;
        } catch (MessagingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MessagingException("WebSocket authentication failed", ex);
        }
    }

    private String resolveAuthorizationHeader(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(Constants.TOKEN_HEADER);
        if (!StringUtils.hasText(header)) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        return header;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private int parseInteger(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return defaultValue;
        }
    }
}
