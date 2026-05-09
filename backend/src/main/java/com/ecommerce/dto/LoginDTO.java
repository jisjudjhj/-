package com.ecommerce.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LoginDTO {

    @NotBlank(message = "登录方式不能为空")
    private String loginType;

    private String username;

    private String phone;

    private String password;

    private String code;

    private String captchaKey;

    private String captchaCode;
}
