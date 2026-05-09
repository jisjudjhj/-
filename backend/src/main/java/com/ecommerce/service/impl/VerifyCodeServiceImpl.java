package com.ecommerce.service.impl;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.service.VerifyCodeService;
import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class VerifyCodeServiceImpl implements VerifyCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerifyCodeServiceImpl.class);
    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final int RATE_LIMIT_SECONDS = 60;
    private static final int IP_RATE_LIMIT_SECONDS = 60;
    private static final int IP_HOURLY_LIMIT_SECONDS = 3600;
    private static final int IP_HOURLY_LIMIT_COUNT = 5;

    @Autowired
    private RedisUtil redisUtil;

    @Value("${verify-code.dev-fixed-code:123456}")
    private String devFixedCode;

    @PostConstruct
    public void logConfiguration() {
        if (StringUtils.hasText(devFixedCode)) {
            log.warn("[verify-code] verify-code.dev-fixed-code is enabled. Disable it outside local development.");
        }
    }

    @Override
    public String generateAndSend(String target, String type, String clientIp) {
        String limitKey = Constants.RedisKey.VERIFY_LIMIT + type + ":" + target;
        String codeKey = Constants.RedisKey.VERIFY_CODE + type + ":" + target;

        if (redisUtil.get(limitKey) != null) {
            throw new BusinessException("请求过于频繁，请稍后再试");
        }
        checkIpRateLimit(type, clientIp);

        String issuedCode = generateCode();
        redisUtil.set(codeKey, issuedCode, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        redisUtil.set(limitKey, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);
        log.info("[verify-code] issued challenge target={} ip={}", maskTarget(target), normalizeIp(clientIp));
        return issuedCode;
    }

    @Override
    public boolean verify(String target, String type, String code) {
        if (StringUtils.hasText(devFixedCode) && devFixedCode.trim().equals(code)) {
            log.warn("[verify-code] dev fixed code accepted target={}", maskTarget(target));
            return true;
        }

        String codeKey = Constants.RedisKey.VERIFY_CODE + type + ":" + target;
        Object cached = redisUtil.get(codeKey);
        if (cached == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!cached.toString().equals(code)) {
            throw new BusinessException("验证码错误");
        }
        redisUtil.delete(codeKey);
        return true;
    }

    private String generateCode() {
        if (StringUtils.hasText(devFixedCode)) {
            return devFixedCode.trim();
        }
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    private void checkIpRateLimit(String type, String clientIp) {
        String normalizedIp = normalizeIp(clientIp);
        String minuteKey = Constants.RedisKey.VERIFY_IP_MINUTE + type + ":" + normalizedIp;
        String hourKey = Constants.RedisKey.VERIFY_IP_HOUR + type + ":" + normalizedIp;

        long minuteCount = readCounter(minuteKey);
        if (minuteCount >= 1) {
            throw new BusinessException("同一 IP 每分钟仅可获取一次验证码");
        }

        long hourCount = readCounter(hourKey);
        if (hourCount >= IP_HOURLY_LIMIT_COUNT) {
            throw new BusinessException("同一 IP 每小时验证码次数已达上限");
        }

        redisUtil.set(minuteKey, String.valueOf(minuteCount + 1), IP_RATE_LIMIT_SECONDS, TimeUnit.SECONDS);
        redisUtil.set(hourKey, String.valueOf(hourCount + 1), IP_HOURLY_LIMIT_SECONDS, TimeUnit.SECONDS);
    }

    private long readCounter(String key) {
        Object value = redisUtil.get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            redisUtil.delete(key);
            return 0L;
        }
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.trim().isEmpty()) {
            return "unknown";
        }
        return clientIp.trim();
    }

    private String maskTarget(String target) {
        if (target == null || target.length() <= 4) {
            return "****";
        }
        return target.substring(0, 3) + "****" + target.substring(target.length() - 2);
    }
}
