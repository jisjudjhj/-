package com.ecommerce.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类，内置本地内存降级。
 */
@Component
public class RedisUtil {

    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final Map<String, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    private boolean redisAvailable = true;
    private long lastCheckTime = 0;
    private static final long RETRY_INTERVAL_MS = 30_000;

    private final ScheduledExecutorService localCacheCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "local-cache-cleaner");
        t.setDaemon(true);
        return t;
    });

    {
        localCacheCleaner.scheduleAtFixedRate(this::evictExpiredEntries, 5, 5, TimeUnit.MINUTES);
    }

    private void evictExpiredEntries() {
        try {
            long now = System.currentTimeMillis();
            int evicted = 0;
            Iterator<Map.Entry<String, LocalCacheEntry>> it = localCache.entrySet().iterator();
            while (it.hasNext()) {
                LocalCacheEntry entry = it.next().getValue();
                if (entry.expireAt != null && now >= entry.expireAt) {
                    it.remove();
                    evicted++;
                }
            }
            if (evicted > 0) {
                log.debug("[RedisUtil] 本地缓存清理: 清除{}个过期条目, 剩余{}个", evicted, localCache.size());
            }
        } catch (Exception e) {
            log.debug("[RedisUtil] 本地缓存清理异常: {}", e.getMessage());
        }
    }

    private static class LocalCacheEntry {
        private final Object value;
        private final Long expireAt;

        private LocalCacheEntry(Object value, Long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private boolean isRedisAvailable() {
        if (redisAvailable) {
            return true;
        }
        if (System.currentTimeMillis() - lastCheckTime > RETRY_INTERVAL_MS) {
            try {
                redisTemplate.opsForValue().get("__health_check__");
                redisAvailable = true;
                log.info("Redis 连接恢复，切回 Redis 缓存");
                localCache.clear();
                return true;
            } catch (Exception e) {
                lastCheckTime = System.currentTimeMillis();
                return false;
            }
        }
        return false;
    }

    private void markUnavailable(Exception e) {
        if (redisAvailable) {
            redisAvailable = false;
            lastCheckTime = System.currentTimeMillis();
            log.warn("Redis 不可用，已降级为本地内存缓存: {}", e.getMessage());
        }
    }

    public void set(String key, Object value) {
        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, value);
                return;
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        putLocal(key, value, null);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, value, timeout, unit);
                return;
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        putLocal(key, value, resolveExpireAt(timeout, unit));
    }

    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        if (isRedisAvailable()) {
            try {
                Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
                return Boolean.TRUE.equals(success);
            } catch (Exception e) {
                markUnavailable(e);
            }
        }

        Long expireAt = resolveExpireAt(timeout, unit);
        synchronized (localCache) {
            LocalCacheEntry current = localCache.get(key);
            if (current != null && !isExpired(current)) {
                return false;
            }
            if (current != null) {
                localCache.remove(key);
            }
            localCache.put(key, new LocalCacheEntry(value, expireAt));
            return true;
        }
    }

    public Object get(String key) {
        if (isRedisAvailable()) {
            try {
                return redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        LocalCacheEntry entry = getLocalEntry(key);
        return entry != null ? entry.value : null;
    }

    public Boolean delete(String key) {
        localCache.remove(key);
        if (isRedisAvailable()) {
            try {
                return redisTemplate.delete(key);
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        return true;
    }

    public Boolean hasKey(String key) {
        if (isRedisAvailable()) {
            try {
                return redisTemplate.hasKey(key);
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        return getLocalEntry(key) != null;
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        if (isRedisAvailable()) {
            try {
                return redisTemplate.expire(key, timeout, unit);
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        LocalCacheEntry entry = getLocalEntry(key);
        if (entry == null) {
            return false;
        }
        putLocal(key, entry.value, resolveExpireAt(timeout, unit));
        return true;
    }

    public Long increment(String key) {
        if (isRedisAvailable()) {
            try {
                return redisTemplate.opsForValue().increment(key);
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        LocalCacheEntry entry = getLocalEntry(key);
        long nextValue = 1L;
        Long expireAt = entry != null ? entry.expireAt : null;
        if (entry != null && entry.value instanceof Number) {
            nextValue = ((Number) entry.value).longValue() + 1;
        }
        putLocal(key, nextValue, expireAt);
        return nextValue;
    }

    public Long incr(String key, long delta) {
        if (delta == 0) {
            Object currentValue = get(key);
            return currentValue instanceof Number ? ((Number) currentValue).longValue() : 0L;
        }
        if (isRedisAvailable()) {
            try {
                return redisTemplate.opsForValue().increment(key, delta);
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        LocalCacheEntry entry = getLocalEntry(key);
        long baseValue = 0L;
        Long expireAt = entry != null ? entry.expireAt : null;
        if (entry != null && entry.value instanceof Number) {
            baseValue = ((Number) entry.value).longValue();
        }
        long nextValue = baseValue + delta;
        putLocal(key, nextValue, expireAt);
        return nextValue;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key) {
        Object obj;
        if (isRedisAvailable()) {
            try {
                obj = redisTemplate.opsForValue().get(key);
                if (obj instanceof List) {
                    return (List<T>) obj;
                }
                return null;
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        LocalCacheEntry entry = getLocalEntry(key);
        obj = entry != null ? entry.value : null;
        if (obj instanceof List) {
            return (List<T>) obj;
        }
        return null;
    }

    public void setList(String key, List<?> list, long timeout, TimeUnit unit) {
        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(key, list, timeout, unit);
                return;
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        putLocal(key, list, resolveExpireAt(timeout, unit));
    }

    @SuppressWarnings("unchecked")
    public Long addToSet(String key, String... values) {
        if (isRedisAvailable()) {
            try {
                return redisTemplate.opsForSet().add(key, (Object[]) values);
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        LocalCacheEntry entry = getLocalEntry(key);
        Set<String> set = new HashSet<>();
        Long expireAt = null;
        if (entry != null) {
            expireAt = entry.expireAt;
            if (entry.value instanceof Set) {
                set.addAll((Set<String>) entry.value);
            }
        }
        long added = 0L;
        if (values != null) {
            for (String value : values) {
                if (value != null && set.add(value)) {
                    added++;
                }
            }
        }
        putLocal(key, set, expireAt);
        return added;
    }

    @SuppressWarnings("unchecked")
    public long getSetSize(String key) {
        if (isRedisAvailable()) {
            try {
                Long size = redisTemplate.opsForSet().size(key);
                return size != null ? size : 0L;
            } catch (Exception e) {
                markUnavailable(e);
            }
        }
        LocalCacheEntry entry = getLocalEntry(key);
        if (entry != null && entry.value instanceof Set) {
            return ((Set<String>) entry.value).size();
        }
        return 0L;
    }

    private void putLocal(String key, Object value, Long expireAt) {
        localCache.put(key, new LocalCacheEntry(value, expireAt));
    }

    private LocalCacheEntry getLocalEntry(String key) {
        LocalCacheEntry entry = localCache.get(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            localCache.remove(key, entry);
            return null;
        }
        return entry;
    }

    private boolean isExpired(LocalCacheEntry entry) {
        return entry.expireAt != null && System.currentTimeMillis() >= entry.expireAt;
    }

    private Long resolveExpireAt(long timeout, TimeUnit unit) {
        if (timeout <= 0 || unit == null) {
            return null;
        }
        return System.currentTimeMillis() + unit.toMillis(timeout);
    }
}
