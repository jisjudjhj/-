package com.ecommerce.service.impl;

import com.ecommerce.config.StreamRealtimeProperties;
import com.ecommerce.service.StreamRealtimeFeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StreamRealtimeFeatureServiceImpl implements StreamRealtimeFeatureService {

    private static final String[] BEHAVIOR_ORDER = new String[]{"view", "cart", "favorite", "purchase", "search"};

    @Autowired
    private StreamRealtimeProperties streamRealtimeProperties;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<Map<String, Object>> getUserBehaviorStats(Long userId) {
        if (!isAvailable(userId)) {
            return Collections.emptyList();
        }
        String key = buildKey(streamRealtimeProperties.getBehaviorStatsKeyPrefix(), userId);
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(key);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Long> normalized = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            String behaviorType = normalizeText(entry.getKey());
            if (!StringUtils.hasText(behaviorType)) {
                continue;
            }
            long value = parseLong(entry.getValue());
            if (value <= 0) {
                continue;
            }
            normalized.merge(behaviorType, value, Long::sum);
        }

        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> consumed = new LinkedHashSet<>();
        for (String behaviorType : BEHAVIOR_ORDER) {
            Long count = normalized.get(behaviorType);
            if (count == null || count <= 0) {
                continue;
            }
            rows.add(buildBehaviorRow(behaviorType, count));
            consumed.add(behaviorType);
        }
        for (Map.Entry<String, Long> entry : normalized.entrySet()) {
            if (consumed.contains(entry.getKey())) {
                continue;
            }
            rows.add(buildBehaviorRow(entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    @Override
    public Map<String, Double> getUserCategoryWeights(Long userId) {
        if (!isAvailable(userId)) {
            return Collections.emptyMap();
        }
        String key = buildKey(streamRealtimeProperties.getCategoryWeightKeyPrefix(), userId);
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(key);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            String category = normalizeText(entry.getKey());
            if (!StringUtils.hasText(category)) {
                continue;
            }
            double weight = parseDouble(entry.getValue());
            if (weight <= 0D) {
                continue;
            }
            result.merge(category, weight, Double::sum);
        }
        return result;
    }

    @Override
    public Set<String> getUserTags(Long userId) {
        if (!isAvailable(userId)) {
            return Collections.emptySet();
        }
        String key = buildKey(streamRealtimeProperties.getTagSetKeyPrefix(), userId);
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String member : members) {
            if (StringUtils.hasText(member)) {
                result.add(member.trim());
            }
        }
        return result;
    }

    private Map<String, Object> buildBehaviorRow(String behaviorType, Long count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("behaviorType", behaviorType);
        row.put("count", count);
        row.put("productCount", count);
        return row;
    }

    private boolean isAvailable(Long userId) {
        return userId != null
                && userId > 0
                && streamRealtimeProperties.isEnabled()
                && stringRedisTemplate != null;
    }

    private String buildKey(String prefix, Long userId) {
        String safePrefix = StringUtils.hasText(prefix) ? prefix.trim() : "stream:user";
        return safePrefix + ":" + userId;
    }

    private String normalizeText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private long parseLong(Object raw) {
        if (raw == null) {
            return 0L;
        }
        try {
            return Math.max(0L, (long) Double.parseDouble(String.valueOf(raw).trim()));
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }

    private double parseDouble(Object raw) {
        if (raw == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException ignore) {
            return 0D;
        }
    }
}

