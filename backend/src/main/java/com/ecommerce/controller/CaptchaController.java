package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.service.CaptchaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    @GetMapping
    public Result<?> getCaptcha() {
        Map<String, String> captchaData = captchaService.generate();
        return Result.success(captchaData);
    }
}
