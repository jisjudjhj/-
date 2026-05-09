package com.ecommerce.interceptor;

import com.alibaba.fastjson2.JSON;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Result;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(Constants.TOKEN_HEADER);
        if (header == null || !header.startsWith(Constants.TOKEN_PREFIX)) {
            sendError(response, 401, "未登录或Token已过期");
            return false;
        }

        String token = header.substring(Constants.TOKEN_PREFIX.length());
        try {
            if (jwtUtil.isTokenExpired(token)) {
                sendError(response, 401, "Token已过期，请重新登录");
                return false;
            }
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.valueOf(claims.get("userId").toString());

            User user = userMapper.selectById(userId);
            if (user == null) {
                sendError(response, 401, "用户不存在");
                return false;
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                sendError(response, 403, "账号已被禁用");
                return false;
            }

            request.setAttribute("userId", userId);
            request.setAttribute("username", claims.getSubject());
            request.setAttribute("role", user.getRole());
            return true;
        } catch (Exception e) {
            sendError(response, 401, "Token无效");
            return false;
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        response.getWriter().write(JSON.toJSONString(
                status == 403 ? Result.forbidden(message) : Result.unauthorized(message)));
    }
}
