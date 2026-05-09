package com.ecommerce.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.RateLimit;
import com.ecommerce.common.Result;
import com.ecommerce.dto.LoginDTO;
import com.ecommerce.dto.PasswordDTO;
import com.ecommerce.dto.RegisterDTO;
import com.ecommerce.dto.ResetPasswordDTO;
import com.ecommerce.dto.SendCodeDTO;
import com.ecommerce.entity.User;
import com.ecommerce.service.CaptchaService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.UserService;
import com.ecommerce.service.VerifyCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "登录、注册、验证码与密码相关接口")
public class AuthController {

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private UserService userService;

    @Autowired
    private VerifyCodeService verifyCodeService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.ecommerce.filter.JwtAuthenticationFilter jwtAuthenticationFilter;

    @PostMapping("/send-code")
    @RateLimit(key = "auth:send-code", window = 60, max = 5, type = RateLimit.LimitType.IP, message = "验证码发送频繁，请稍后再试")
    @Operation(summary = "发送手机验证码", description = "用于注册、登录或重置密码前的手机验证码发送。")
    public Result<?> sendCode(@Validated @RequestBody SendCodeDTO dto, HttpServletRequest request) {
        if (!"phone".equals(dto.getType())) {
            throw new BusinessException("仅支持手机号验证码");
        }
        if (!StringUtils.hasText(dto.getPhone())) {
            throw new BusinessException("手机号不能为空");
        }
        verifyCodeService.generateAndSend(dto.getPhone(), "phone", getClientIp(request));
        return Result.success("验证码已发送");
    }

    @PostMapping("/register")
    @RateLimit(key = "auth:register", window = 60, max = 10, type = RateLimit.LimitType.IP, message = "注册请求频繁，请稍后再试")
    @Operation(summary = "用户注册", description = "手机号注册新用户账号。")
    public Result<?> register(@Validated @RequestBody RegisterDTO dto) {
        moduleSwitchService.requireEnabled("register");
        captchaService.verify(dto.getCaptchaKey(), dto.getCaptchaCode());
        verifyCodeService.verify(dto.getPhone(), "phone", dto.getCode());
        User user = userService.registerByPhone(
                dto.getPhone(), dto.getPassword(), dto.getNickname());
        user.setPassword(null);
        return Result.success("注册成功", user);
    }

    @PostMapping("/login")
    @RateLimit(key = "auth:login", window = 60, max = 20, type = RateLimit.LimitType.IP, message = "登录请求过于频繁，请稍后再试")
    @Operation(summary = "用户登录", description = "支持项目内配置的登录方式，返回用户信息与 JWT Token。")
    public Result<?> login(@Validated @RequestBody LoginDTO dto) {
        captchaService.verify(dto.getCaptchaKey(), dto.getCaptchaCode());
        String loginType = dto.getLoginType();
        Map<String, Object> data;

        switch (loginType) {
            case Constants.LoginType.PASSWORD:
                String account = StringUtils.hasText(dto.getUsername()) ? dto.getUsername() : dto.getPhone();
                data = userService.loginByPassword(account, dto.getPassword());
                break;

            case Constants.LoginType.PHONE_CODE:
                if (!StringUtils.hasText(dto.getPhone()) || !StringUtils.hasText(dto.getCode())) {
                    throw new BusinessException("手机号和验证码不能为空");
                }
                verifyCodeService.verify(dto.getPhone(), "phone", dto.getCode());
                data = userService.loginByPhoneCode(dto.getPhone());
                break;

            default:
                throw new BusinessException("不支持的登录方式");
        }

        return Result.success("登录成功", data);
    }

    @PostMapping("/reset-password")
    public Result<?> resetPassword(@Validated @RequestBody ResetPasswordDTO dto) {
        verifyCodeService.verify(dto.getPhone(), "phone", dto.getCode());
        Long userId = userService.resetPassword(dto.getPhone(), dto.getNewPassword());
        jwtAuthenticationFilter.evictAuthCache(userId);
        return Result.success("密码重置成功");
    }

    @GetMapping("/me")
    public Result<?> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    @PutMapping("/profile")
    public Result<?> updateProfile(@RequestBody Map<String, String> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.submitProfileChange(userId, params.get("nickname"), params.get("avatar"));
        return Result.success("修改申请已提交，等待审核");
    }

    @GetMapping("/profile/change-status")
    public Result<?> getProfileChangeStatus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userService.getProfileChangeStatus(userId));
    }

    @PutMapping("/password")
    public Result<?> changePassword(@Validated @RequestBody PasswordDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }
        int newVersion = (user.getTokenVersion() != null ? user.getTokenVersion() : 0) + 1;
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        update.setTokenVersion(newVersion);
        userService.updateById(update);
        jwtAuthenticationFilter.evictAuthCache(userId);
        return Result.success("密码修改成功");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return StringUtils.hasText(ip) ? ip : "unknown";
    }
}
