package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.entity.User;
import com.ecommerce.service.UserService;
import com.ecommerce.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/game/auth")
@Tag(name = "GameAuth", description = "游戏端 Token 登录校验接口")
public class GameAuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @RequestMapping(value = "/verify-token", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "游戏端 Token 校验", description = "校验登录接口返回的 JWT Token，并返回游戏端登录回调数据。")
    public Result<?> verifyToken(@RequestParam(required = false) String token,
                                 @RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestBody(required = false) Map<String, Object> body) {
        String normalizedToken = normalizeToken(resolveToken(token, authorization, body));
        if (!StringUtils.hasText(normalizedToken)) {
            return Result.unauthorized("Token不能为空");
        }

        try {
            Claims claims = jwtUtil.parseToken(normalizedToken);
            if (claims.getExpiration() == null || claims.getExpiration().before(new java.util.Date())) {
                return Result.unauthorized("Token已过期");
            }

            Long userId = Long.valueOf(claims.get("userId").toString());
            User user = userService.getById(userId);
            if (user == null || user.getDeleted() != null && user.getDeleted() != 0) {
                return Result.unauthorized("用户不存在");
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                return Result.unauthorized("账号已被禁用");
            }

            int claimVersion = claims.get("tokenVersion") != null
                    ? Integer.parseInt(claims.get("tokenVersion").toString()) : 0;
            int currentVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
            if (currentVersion > claimVersion) {
                return Result.unauthorized("Token已失效，请重新登录");
            }

            return Result.success("Token校验成功", buildGameLoginData(normalizedToken, user));
        } catch (Exception e) {
            return Result.unauthorized("Token校验失败");
        }
    }

    private String resolveToken(String token, String authorization, Map<String, Object> body) {
        if (StringUtils.hasText(token)) {
            return token;
        }
        if (StringUtils.hasText(authorization)) {
            return authorization;
        }
        if (body == null) {
            return null;
        }
        Object value = body.get("token");
        if (value == null) {
            value = body.get("accessToken");
        }
        if (value == null) {
            value = body.get("Authorization");
        }
        return value != null ? value.toString() : null;
    }

    private String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String value = token.trim();
        if (value.startsWith("Bearer ")) {
            return value.substring("Bearer ".length()).trim();
        }
        return value;
    }

    private Map<String, Object> buildGameLoginData(String token, User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        String userId = String.valueOf(user.getId());
        String username = StringUtils.hasText(user.getUsername()) ? user.getUsername() : userId;
        String nickname = StringUtils.hasText(user.getNickname()) ? user.getNickname() : username;
        data.put("token", token);
        data.put("openid", userId);
        data.put("userId", user.getId());
        data.put("username", username);
        data.put("nickname", nickname);
        data.put("phone", user.getPhone());
        data.put("role", user.getRole());
        data.put("account", username);
        data.put("sbid", 0);
        data.put("areaid", "0");
        data.put("isAuthenticated", true);
        data.put("changePS", "1");
        data.put("isOPPOPlatform", false);
        data.put("isOPPOVIP", false);
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }
}
