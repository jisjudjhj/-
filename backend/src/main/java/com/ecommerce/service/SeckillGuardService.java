package com.ecommerce.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class SeckillGuardService {

    private static final String IDEMPOTENCY_LOCK_PREFIX = "seckill:idempotency:lock:";
    private static final String IDEMPOTENCY_RESULT_PREFIX = "seckill:idempotency:result:";
    private static final String INFLIGHT_PREFIX = "seckill:inflight:";

    @Autowired
    private RedisUtil redisUtil;

    @Value("${seckill.guard.idempotency-lock-seconds:30}")
    private int idempotencyLockSeconds;

    @Value("${seckill.guard.idempotency-result-seconds:900}")
    private int idempotencyResultSeconds;

    @Value("${seckill.guard.auto-key-window-seconds:10}")
    private int autoKeyWindowSeconds;

    @Value("${seckill.guard.inflight-limit-per-apply:80}")
    private int inflightLimitPerApply;

    @Value("${seckill.guard.inflight-key-expire-seconds:3}")
    private int inflightKeyExpireSeconds;

    @Value("${seckill.guard.queue-wait-ms:200}")
    private int queueWaitMs;

    @Value("${seckill.guard.queue-retry-interval-ms:25}")
    private int queueRetryIntervalMs;

    public String resolveIdempotencyKey(Long userId, Long applyId, Integer quantity, String clientKey) {
        if (StringUtils.hasText(clientKey)) {
            return clientKey.trim();
        }
        int safeWindow = Math.max(1, autoKeyWindowSeconds);
        long bucket = System.currentTimeMillis() / (safeWindow * 1000L);
        int safeQuantity = quantity == null ? 1 : Math.max(1, quantity);
        return "auto:" + userId + ":" + applyId + ":" + safeQuantity + ":" + bucket;
    }

    public Long getCompletedOrderId(Long userId, String idempotencyKey) {
        Object value = redisUtil.get(buildResultKey(userId, idempotencyKey));
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public boolean tryAcquireIdempotencyLock(Long userId, String idempotencyKey) {
        return Boolean.TRUE.equals(redisUtil.setIfAbsent(
                buildLockKey(userId, idempotencyKey),
                "1",
                Math.max(1, idempotencyLockSeconds),
                TimeUnit.SECONDS
        ));
    }

    public void markIdempotencySuccess(Long userId, String idempotencyKey, Long orderId) {
        redisUtil.set(
                buildResultKey(userId, idempotencyKey),
                orderId,
                Math.max(30, idempotencyResultSeconds),
                TimeUnit.SECONDS
        );
        clearIdempotencyLock(userId, idempotencyKey);
    }

    public void clearIdempotencyLock(Long userId, String idempotencyKey) {
        redisUtil.delete(buildLockKey(userId, idempotencyKey));
    }

    public void acquireHotspotPermit(Long applyId) {
        int retryInterval = Math.max(5, queueRetryIntervalMs);
        int retries = Math.max(0, queueWaitMs / retryInterval);
        for (int i = 0; i <= retries; i++) {
            if (tryAcquirePermitOnce(applyId)) {
                return;
            }
            if (i < retries) {
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException("秒杀请求繁忙，请稍后重试");
                }
            }
        }
        throw new BusinessException("当前秒杀过于火爆，请稍后重试");
    }

    public void releaseHotspotPermit(Long applyId) {
        String key = buildInflightKey(applyId);
        Long left = redisUtil.incr(key, -1);
        if (left == null || left <= 0) {
            redisUtil.delete(key);
        }
    }

    private boolean tryAcquirePermitOnce(Long applyId) {
        String key = buildInflightKey(applyId);
        Long current = redisUtil.incr(key, 1);
        if (current == null) {
            current = 1L;
        }
        if (current == 1L) {
            redisUtil.expire(key, Math.max(1, inflightKeyExpireSeconds), TimeUnit.SECONDS);
        }
        if (current > Math.max(1, inflightLimitPerApply)) {
            redisUtil.incr(key, -1);
            return false;
        }
        return true;
    }

    private String buildLockKey(Long userId, String idempotencyKey) {
        return IDEMPOTENCY_LOCK_PREFIX + userId + ":" + idempotencyKey;
    }

    private String buildResultKey(Long userId, String idempotencyKey) {
        return IDEMPOTENCY_RESULT_PREFIX + userId + ":" + idempotencyKey;
    }

    private String buildInflightKey(Long applyId) {
        return INFLIGHT_PREFIX + applyId;
    }
}
