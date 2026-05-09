package com.ecommerce.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.lang.UUID;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.service.CaptchaService;
import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaServiceImpl.class);
    private static final int CAPTCHA_WIDTH = 160;
    private static final int CAPTCHA_HEIGHT = 60;
    private static final int CAPTCHA_CODE_COUNT = 4;
    private static final int CAPTCHA_LINE_COUNT = 30;
    private static final int CAPTCHA_EXPIRE_MINUTES = 5;

    private final Map<String, String[]> memoryStore = new ConcurrentHashMap<>();

    @Autowired
    private RedisUtil redisUtil;

    private boolean redisAvailable = true;

    @Override
    public Map<String, String> generate() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(
                CAPTCHA_WIDTH, CAPTCHA_HEIGHT, CAPTCHA_CODE_COUNT, CAPTCHA_LINE_COUNT);

        String code = captcha.getCode().toLowerCase();
        String imageBase64 = captcha.getImageBase64Data();
        String captchaKey = UUID.randomUUID().toString(true);

        String redisKey = Constants.RedisKey.CAPTCHA_CODE + captchaKey;

        if (redisAvailable) {
            try {
                redisUtil.set(redisKey, code, CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.info("[captcha] stored challenge in Redis, key={}", captchaKey);
            } catch (Exception e) {
                log.warn("[captcha] Redis unavailable, falling back to memory: {}", e.getMessage());
                redisAvailable = false;
                storeInMemory(captchaKey, code);
            }
        } else {
            storeInMemory(captchaKey, code);
        }

        Map<String, String> result = new HashMap<>();
        result.put("captchaKey", captchaKey);
        result.put("captchaImage", imageBase64);
        return result;
    }

    @Override
    public boolean verify(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaKey.isEmpty()
                || captchaCode == null || captchaCode.isEmpty()) {
            throw new BusinessException("璇疯緭鍏ュ浘褰㈤獙璇佺爜");
        }

        String redisKey = Constants.RedisKey.CAPTCHA_CODE + captchaKey;
        String storedCode = null;

        if (redisAvailable) {
            try {
                Object cached = redisUtil.get(redisKey);
                if (cached != null) {
                    storedCode = cached.toString();
                }
                redisUtil.delete(redisKey);
            } catch (Exception e) {
                log.warn("[captcha] Redis verification failed, trying memory fallback");
            }
        }

        if (storedCode == null) {
            storedCode = getFromMemory(captchaKey);
        }

        if (storedCode == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }

        if (!storedCode.equals(captchaCode.toLowerCase())) {
            throw new BusinessException("图形验证码错误");
        }

        return true;
    }

    private void storeInMemory(String key, String code) {
        cleanExpired();
        long expire = System.currentTimeMillis() + CAPTCHA_EXPIRE_MINUTES * 60_000L;
        memoryStore.put(key, new String[]{code, String.valueOf(expire)});
        log.info("[captcha] stored challenge in memory fallback, key={}", key);
    }

    private String getFromMemory(String key) {
        String[] data = memoryStore.remove(key);
        if (data == null) {
            return null;
        }
        if (System.currentTimeMillis() > Long.parseLong(data[1])) {
            return null;
        }
        return data[0];
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        memoryStore.entrySet().removeIf(e -> now > Long.parseLong(e.getValue()[1]));
    }
}
