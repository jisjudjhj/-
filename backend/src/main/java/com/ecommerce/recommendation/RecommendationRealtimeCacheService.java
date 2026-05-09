package com.ecommerce.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.ecommerce.entity.StreamProductHotnessRealtime;
import com.ecommerce.entity.StreamUserCategoryPreference;
import com.ecommerce.mapper.StreamProductHotnessRealtimeMapper;
import com.ecommerce.mapper.StreamUserCategoryPreferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.zip.CRC32;

@Service
public class RecommendationRealtimeCacheService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationRealtimeCacheService.class);
    private static final long DEFAULT_TTL_SECONDS = 180L;
    private static final long MIN_TTL_SECONDS = 60L;
    private static final long MAX_TTL_SECONDS = 300L;
    private static final String CACHE_KEY_PREFIX = "recommend:rt";
    private static final String CACHE_KEY_USER_PREF = CACHE_KEY_PREFIX + ":pref:user";
    private static final String CACHE_KEY_HOT_TOP = CACHE_KEY_PREFIX + ":hot:top";
    private static final String CACHE_KEY_HOT_MAP = CACHE_KEY_PREFIX + ":hot:map";
    private static final String VERSION_NONE = "none";

    @Autowired
    private StreamUserCategoryPreferenceMapper streamUserCategoryPreferenceMapper;

    @Autowired
    private StreamProductHotnessRealtimeMapper streamProductHotnessRealtimeMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${recommendation.realtime-cache-ttl-seconds:180}")
    private long cacheTtlSeconds;

    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final AtomicLong redisHitCount = new AtomicLong();
    private final AtomicLong localHitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong loadCount = new AtomicLong();
    private final AtomicLong invalidateCount = new AtomicLong();

    public List<StreamUserCategoryPreference> getUserPreferenceRows(Long userId, int limit) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        final int safeLimit = Math.max(1, limit);
        String version = resolveUserPreferenceVersion(userId);
        String key = CACHE_KEY_USER_PREF + ":" + userId + ":v:" + version + ":limit:" + safeLimit;
        return readThrough(key, new TypeReference<List<StreamUserCategoryPreference>>() {}, () -> {
            try {
                List<StreamUserCategoryPreference> rows =
                        streamUserCategoryPreferenceMapper.selectTopByUserId(userId, safeLimit);
                return rows == null ? Collections.<StreamUserCategoryPreference>emptyList() : new ArrayList<>(rows);
            } catch (Exception exception) {
                log.debug("[RealtimeCache] load user preference failed userId={}, fallback empty: {}",
                        userId, exception.getMessage());
                return Collections.emptyList();
            }
        });
    }

    public boolean hasUserPreferenceSignal(Long userId) {
        return !getUserPreferenceRows(userId, 1).isEmpty();
    }

    public LocalDateTime getUserPreferenceLatestUpdateTime(Long userId) {
        List<StreamUserCategoryPreference> rows = getUserPreferenceRows(userId, 1);
        if (rows.isEmpty()) {
            return null;
        }
        StreamUserCategoryPreference row = rows.get(0);
        return row == null ? null : row.getUpdateTime();
    }

    public List<StreamProductHotnessRealtime> getHotRows(int limit) {
        final int safeLimit = Math.max(1, limit);
        String version = resolveHotVersion();
        String key = CACHE_KEY_HOT_TOP + ":v:" + version + ":limit:" + safeLimit;
        return readThrough(key, new TypeReference<List<StreamProductHotnessRealtime>>() {}, () -> {
            try {
                List<StreamProductHotnessRealtime> rows = streamProductHotnessRealtimeMapper.selectList(
                        new LambdaQueryWrapper<StreamProductHotnessRealtime>()
                                .isNotNull(StreamProductHotnessRealtime::getProductId)
                                .gt(StreamProductHotnessRealtime::getHotScore, 0D)
                                .orderByDesc(StreamProductHotnessRealtime::getHotScore)
                                .orderByDesc(StreamProductHotnessRealtime::getPurchaseCount)
                                .orderByDesc(StreamProductHotnessRealtime::getBehaviorCount)
                                .orderByDesc(StreamProductHotnessRealtime::getUpdateTime)
                                .last("LIMIT " + safeLimit));
                return rows == null ? Collections.<StreamProductHotnessRealtime>emptyList() : new ArrayList<>(rows);
            } catch (Exception exception) {
                log.debug("[RealtimeCache] load hot rows failed, fallback empty: {}", exception.getMessage());
                return Collections.emptyList();
            }
        });
    }

    public boolean hasHotSignal() {
        return !getHotRows(1).isEmpty();
    }

    public void invalidateUser(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        invalidateByPrefix(CACHE_KEY_USER_PREF + ":" + userId + ":");
    }

    public void invalidateHot() {
        invalidateByPrefix(CACHE_KEY_HOT_TOP + ":");
        invalidateByPrefix(CACHE_KEY_HOT_MAP + ":");
    }

    public void invalidateAll() {
        invalidateByPrefix(CACHE_KEY_PREFIX + ":");
    }

    public Map<String, Object> getCacheStatsSnapshot() {
        long redisHits = redisHitCount.get();
        long localHits = localHitCount.get();
        long misses = missCount.get();
        long totalReads = redisHits + localHits + misses;
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("redisHitCount", redisHits);
        stats.put("localHitCount", localHits);
        stats.put("missCount", misses);
        stats.put("loadCount", loadCount.get());
        stats.put("invalidateCount", invalidateCount.get());
        stats.put("localEntryCount", cache.size());
        stats.put("ttlSeconds", ttlSeconds());
        stats.put("hitRate", totalReads <= 0 ? 0D : ((redisHits + localHits) * 100D / totalReads));
        return stats;
    }

    public Map<Long, StreamProductHotnessRealtime> getHotRowsByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> safeIds = new LinkedHashSet<>();
        for (Long productId : productIds) {
            if (productId != null && productId > 0) {
                safeIds.add(productId);
            }
        }
        if (safeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> sortedIds = new ArrayList<>(safeIds);
        sortedIds.sort(Comparator.naturalOrder());
        String version = resolveHotVersion();
        String key = CACHE_KEY_HOT_MAP + ":v:" + version + ":ids:" + hashIds(sortedIds);

        List<StreamProductHotnessRealtime> rows = readThrough(
                key,
                new TypeReference<List<StreamProductHotnessRealtime>>() {},
                () -> {
                    if (sortedIds.isEmpty()) {
                        return Collections.emptyList();
                    }
                    List<StreamProductHotnessRealtime> loaded;
                    try {
                        loaded = streamProductHotnessRealtimeMapper.selectList(
                                new LambdaQueryWrapper<StreamProductHotnessRealtime>()
                                        .in(StreamProductHotnessRealtime::getProductId, sortedIds));
                    } catch (Exception exception) {
                        log.debug("[RealtimeCache] load hot map failed ids={}, fallback empty: {}",
                                sortedIds.size(), exception.getMessage());
                        return Collections.emptyList();
                    }
                    if (loaded == null || loaded.isEmpty()) {
                        return Collections.emptyList();
                    }
                    return new ArrayList<>(loaded);
                });

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, StreamProductHotnessRealtime> result = new LinkedHashMap<>();
        for (StreamProductHotnessRealtime row : rows) {
            if (row != null && row.getProductId() != null) {
                result.put(row.getProductId(), row);
            }
        }
        if (result.isEmpty()) {
            return Collections.emptyMap();
        }
        if (result.size() == safeIds.size()) {
            return result;
        }
        Map<Long, StreamProductHotnessRealtime> filtered = new LinkedHashMap<>();
        for (Long productId : safeIds) {
            StreamProductHotnessRealtime row = result.get(productId);
            if (row != null) {
                filtered.put(productId, row);
            }
        }
        return filtered;
    }

    private String resolveUserPreferenceVersion(Long userId) {
        LocalDateTime latest = queryLatestUserPreferenceUpdateTime(userId);
        return versionOf(latest);
    }

    private String resolveHotVersion() {
        LocalDateTime latest = queryLatestHotUpdateTime();
        return versionOf(latest);
    }

    private LocalDateTime queryLatestUserPreferenceUpdateTime(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        try {
            StreamUserCategoryPreference row = streamUserCategoryPreferenceMapper.selectLatestByUserId(userId);
            return row == null ? null : row.getUpdateTime();
        } catch (Exception exception) {
            log.debug("[RealtimeCache] query latest preference version failed userId={}, fallback none: {}",
                    userId, exception.getMessage());
            return null;
        }
    }

    private LocalDateTime queryLatestHotUpdateTime() {
        try {
            StreamProductHotnessRealtime row = streamProductHotnessRealtimeMapper.selectOne(
                    new LambdaQueryWrapper<StreamProductHotnessRealtime>()
                            .select(StreamProductHotnessRealtime::getUpdateTime)
                            .orderByDesc(StreamProductHotnessRealtime::getUpdateTime)
                            .last("LIMIT 1"));
            return row == null ? null : row.getUpdateTime();
        } catch (Exception exception) {
            log.debug("[RealtimeCache] query latest hot version failed, fallback none: {}",
                    exception.getMessage());
            return null;
        }
    }

    private String versionOf(LocalDateTime updateTime) {
        if (updateTime == null) {
            return VERSION_NONE;
        }
        return String.valueOf(updateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    @SuppressWarnings("unchecked")
    private <T> T readThrough(String key, TypeReference<T> typeReference, Supplier<T> supplier) {
        T redisValue = readRedis(key, typeReference);
        if (redisValue != null) {
            redisHitCount.incrementAndGet();
            putLocal(key, redisValue);
            return redisValue;
        }

        long now = System.currentTimeMillis();
        CacheEntry<?> localEntry = cache.get(key);
        if (localEntry != null && localEntry.expireAt > now) {
            localHitCount.incrementAndGet();
            return (T) localEntry.value;
        }

        missCount.incrementAndGet();
        T loaded = supplier.get();
        loadCount.incrementAndGet();
        putLocal(key, loaded);
        writeRedis(key, loaded);
        return loaded;
    }

    private <T> T readRedis(String key, TypeReference<T> typeReference) {
        if (stringRedisTemplate == null || !StringUtils.hasText(key)) {
            return null;
        }
        try {
            String raw = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return JSON.parseObject(raw, typeReference);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeRedis(String key, Object value) {
        if (stringRedisTemplate == null || !StringUtils.hasText(key)) {
            return;
        }
        try {
            String payload = JSON.toJSONString(value == null ? Collections.emptyList() : value);
            stringRedisTemplate.opsForValue().set(key, payload, ttlDuration());
        } catch (Exception ignored) {
        }
    }

    private void invalidateByPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return;
        }
        invalidateCount.incrementAndGet();
        cache.keySet().removeIf(key -> key != null && key.startsWith(prefix));
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = stringRedisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception exception) {
            log.debug("[RealtimeCache] invalidate prefix failed prefix={}, reason={}", prefix, exception.getMessage());
        }
    }

    private void putLocal(String key, Object value) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        long now = System.currentTimeMillis();
        cache.put(key, new CacheEntry<>(value, now + ttlMillis()));
    }

    private String hashIds(List<Long> sortedIds) {
        if (sortedIds == null || sortedIds.isEmpty()) {
            return "empty";
        }
        CRC32 crc32 = new CRC32();
        for (Long id : sortedIds) {
            byte[] bytes = String.valueOf(id).getBytes(StandardCharsets.UTF_8);
            crc32.update(bytes, 0, bytes.length);
            crc32.update((byte) ',');
        }
        return Long.toHexString(crc32.getValue());
    }

    private Duration ttlDuration() {
        return Duration.ofSeconds(ttlSeconds());
    }

    private long ttlSeconds() {
        long configured = cacheTtlSeconds <= 0 ? DEFAULT_TTL_SECONDS : cacheTtlSeconds;
        return Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, configured));
    }

    private long ttlMillis() {
        return ttlSeconds() * 1000L;
    }

    private static class CacheEntry<T> {
        private final T value;
        private final long expireAt;

        private CacheEntry(T value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
