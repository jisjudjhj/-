package com.ecommerce.service;

import java.util.Map;

public interface CaptchaService {

    /**
     * 生成图形验证码
     * @return {captchaKey, captchaImage(base64)}
     */
    Map<String, String> generate();

    /**
     * 校验图形验证码
     * @param captchaKey  验证码唯一标识
     * @param captchaCode 用户输入的验证码文字
     * @return 是否正确
     */
    boolean verify(String captchaKey, String captchaCode);
}
