package com.ecommerce.service;

import com.ecommerce.entity.Product;
import com.ecommerce.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class RedisStockService {

    private static final Logger log = LoggerFactory.getLogger(RedisStockService.class);
    private static final String STOCK_KEY_PREFIX = "stock:product:";

    private static final DefaultRedisScript<Long> DEDUCT_STOCK_SCRIPT = new DefaultRedisScript<>();

    static {
        DEDUCT_STOCK_SCRIPT.setResultType(Long.class);
        DEDUCT_STOCK_SCRIPT.setScriptText(
                "local stock = redis.call('GET', KEYS[1]);" +
                        "if (not stock) then return -1 end;" +
                        "stock = tonumber(stock);" +
                        "local deduct = tonumber(ARGV[1]);" +
                        "if (stock < deduct) then return 0 end;" +
                        "redis.call('DECRBY', KEYS[1], deduct);" +
                        "return 1;"
        );
    }

    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${order.stock.redis-enabled:true}")
    private boolean redisEnabled;

    @Value("${order.stock.redis-key-ttl-seconds:21600}")
    private long redisStockKeyTtlSeconds;

    public RedisStockService(ProductMapper productMapper,
                             StringRedisTemplate stringRedisTemplate) {
        this.productMapper = productMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean deductStock(Long productId, int quantity) {
        if (productId == null || quantity <= 0) {
            return false;
        }

        if (!redisEnabled || stringRedisTemplate == null) {
            return productMapper.deductStock(productId, quantity) > 0;
        }

        int redisResult = tryDeductFromRedis(productId, quantity);
        if (redisResult == 0) {
            return false;
        }

        int dbAffected = productMapper.deductStock(productId, quantity);
        if (dbAffected > 0) {
            return true;
        }

        if (redisResult == 1) {
            compensateRedisStock(productId, quantity);
        }
        return false;
    }

    public void restoreStock(Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity <= 0) {
            return;
        }

        productMapper.restoreStock(productId, quantity);
        compensateRedisStock(productId, quantity);
    }

    public void compensateRedisStock(Long productId, int quantity) {
        if (productId == null || quantity <= 0 || !redisEnabled || stringRedisTemplate == null) {
            return;
        }
        try {
            String key = buildStockKey(productId);
            Boolean exists = stringRedisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                stringRedisTemplate.opsForValue().increment(key, quantity);
            }
        } catch (Exception ex) {
            log.debug("[RedisStock] compensate failed, productId={}, quantity={}, error={}",
                    productId, quantity, ex.getMessage());
        }
    }

    public void evictStockCache(Long productId) {
        if (productId == null || !redisEnabled || stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(buildStockKey(productId));
        } catch (Exception ex) {
            log.debug("[RedisStock] evict key failed, productId={}, error={}", productId, ex.getMessage());
        }
    }

    private int tryDeductFromRedis(Long productId, int quantity) {
        String key = buildStockKey(productId);
        try {
            ensureStockKeyInitialized(productId, key);
            Long result = stringRedisTemplate.execute(
                    DEDUCT_STOCK_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(quantity)
            );
            if (result == null) {
                return -1;
            }
            if (result.longValue() == -1L) {
                ensureStockKeyInitialized(productId, key);
                Long retryResult = stringRedisTemplate.execute(
                        DEDUCT_STOCK_SCRIPT,
                        Collections.singletonList(key),
                        String.valueOf(quantity)
                );
                if (retryResult == null) {
                    return -1;
                }
                return retryResult.intValue();
            }
            return result.intValue();
        } catch (Exception ex) {
            log.warn("[RedisStock] redis deduct fallback to db, productId={}, quantity={}, error={}",
                    productId, quantity, ex.getMessage());
            return -1;
        }
    }

    private void ensureStockKeyInitialized(Long productId, String key) {
        try {
            Boolean exists = stringRedisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                return;
            }
            Product product = productMapper.selectById(productId);
            if (product == null || product.getStock() == null) {
                return;
            }
            long ttlSeconds = withJitter(redisStockKeyTtlSeconds, 0.2D);
            stringRedisTemplate.opsForValue().setIfAbsent(
                    key,
                    String.valueOf(Math.max(0, product.getStock())),
                    ttlSeconds,
                    TimeUnit.SECONDS
            );
        } catch (Exception ex) {
            log.debug("[RedisStock] init stock key failed, productId={}, error={}", productId, ex.getMessage());
        }
    }

    private String buildStockKey(Long productId) {
        return STOCK_KEY_PREFIX + productId;
    }

    private long withJitter(long baseSeconds, double ratio) {
        long safeBase = Math.max(60L, baseSeconds);
        long maxJitter = (long) (safeBase * Math.max(0D, ratio));
        if (maxJitter <= 0L) {
            return safeBase;
        }
        long randomJitter = ThreadLocalRandom.current().nextLong(maxJitter + 1L);
        return safeBase + randomJitter;
    }
}
