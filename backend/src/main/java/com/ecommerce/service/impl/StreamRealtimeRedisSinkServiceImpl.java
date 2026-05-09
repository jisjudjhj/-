package com.ecommerce.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ecommerce.config.StreamRealtimeProperties;
import com.ecommerce.service.StreamRealtimeRedisSinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StreamRealtimeRedisSinkServiceImpl implements StreamRealtimeRedisSinkService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String RECOMMENDATION_KPI_KEY_PREFIX = "stream:recommendation:kpi:date";
    private static final String RECOMMENDATION_KPI_LATEST_KEY = "stream:recommendation:kpi:latest";
    private static final String RECOMMENDATION_KPI_LAST_UPDATE_KEY = "stream:recommendation:kpi:lastUpdate";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private StreamRealtimeProperties streamRealtimeProperties;

    @Override
    public void acceptUserBehaviorDistribution(String rawJson) {
        JSONObject json = parseJson(rawJson);
        Long userId = parseLong(firstNonNull(json.get("userId"), json.get("user_id")));
        String behaviorType = readText(firstNonNull(json.get("behaviorType"), json.get("behavior_type")));
        Long count = parseLong(firstNonNull(json.get("count"), json.get("behaviorCount"), json.get("behavior_count")));

        if (userId == null || userId <= 0 || !StringUtils.hasText(behaviorType) || count == null || count < 0) {
            throw new IllegalArgumentException("invalid user behavior distribution payload");
        }

        String key = buildBehaviorKey(userId);
        stringRedisTemplate.opsForHash().put(key, behaviorType, String.valueOf(count));
        touchUserFeatureTimestamp(userId, "behavior");
    }

    @Override
    public void acceptUserCategoryPreference(String rawJson) {
        JSONObject json = parseJson(rawJson);
        Long userId = parseLong(firstNonNull(json.get("userId"), json.get("user_id")));
        String categoryName = readText(firstNonNull(json.get("categoryName"), json.get("category_name"), json.get("category")));
        Double weight = parseDouble(firstNonNull(json.get("weight"), json.get("score")));

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("invalid user category preference payload");
        }

        if (StringUtils.hasText(categoryName) && weight != null && weight > 0D) {
            stringRedisTemplate.opsForHash().put(buildCategoryWeightKey(userId), categoryName, String.valueOf(weight));
        }

        Object tagsRaw = firstNonNull(json.get("tags"), json.get("tagList"), json.get("tag_list"));
        Set<String> tags = parseTags(tagsRaw);
        if (!tags.isEmpty()) {
            stringRedisTemplate.opsForSet().add(buildTagKey(userId), tags.toArray(new String[0]));
        }

        touchUserFeatureTimestamp(userId, "category");
    }

    @Override
    public void acceptProductHotness(String rawJson) {
        JSONObject json = parseJson(rawJson);
        Long productId = parseLong(firstNonNull(json.get("productId"), json.get("product_id")));
        Double score = parseDouble(firstNonNull(json.get("score"), json.get("hotScore"), json.get("hot_score")));
        String window = readText(firstNonNull(json.get("window"), json.get("bucket"), json.get("timeWindow")));

        if (productId == null || productId <= 0 || score == null) {
            throw new IllegalArgumentException("invalid product hotness payload");
        }
        if (!StringUtils.hasText(window)) {
            window = "1h";
        }
        window = normalizeWindow(window);

        String key = "stream:product:hot:" + window;
        stringRedisTemplate.opsForZSet().add(key, String.valueOf(productId), score);
        stringRedisTemplate.opsForValue().set("stream:product:hot:lastUpdate", nowText());
        stringRedisTemplate.opsForValue().set("stream:product:hot:lastUpdate:" + window, nowText());
    }

    @Override
    public void acceptRecommendationCoreMetrics(String rawJson) {
        JSONObject json = parseJson(rawJson);
        String statDate = normalizeStatDate(readText(firstNonNull(
                json.get("statDate"), json.get("stat_date"), json.get("eventDay"), json.get("event_day"))));
        if (!StringUtils.hasText(statDate)) {
            statDate = LocalDate.now().toString();
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("statDate", statDate);

        putIfValidLong(snapshot, "dau", firstNonNull(json.get("dau"), json.get("DAU")));
        putIfValidLong(snapshot, "exposureCount", firstNonNull(json.get("exposureCount"), json.get("exposure_count")));
        putIfValidLong(snapshot, "clickCount", firstNonNull(json.get("clickCount"), json.get("click_count")));
        putIfValidLong(snapshot, "orderCount", firstNonNull(json.get("orderCount"), json.get("order_count")));
        putIfValidLong(snapshot, "refundCount", firstNonNull(json.get("refundCount"), json.get("refund_count")));
        putIfValidLong(snapshot, "orderUserCount", firstNonNull(json.get("orderUserCount"), json.get("order_user_count")));
        putIfValidLong(snapshot, "repurchaseUserCount", firstNonNull(json.get("repurchaseUserCount"), json.get("repurchase_user_count")));
        putIfValidLong(snapshot, "retainedUserCount", firstNonNull(json.get("retainedUserCount"), json.get("retained_user_count")));
        putIfValidLong(snapshot, "retentionBaseUserCount", firstNonNull(json.get("retentionBaseUserCount"), json.get("retention_base_user_count")));

        putIfValidDouble(snapshot, "gmv", firstNonNull(json.get("gmv"), json.get("GMV")));
        putIfValidDouble(snapshot, "refundAmount", firstNonNull(json.get("refundAmount"), json.get("refund_amount")));
        putIfValidDouble(snapshot, "ctr", firstNonNull(json.get("ctr"), json.get("CTR")));
        putIfValidDouble(snapshot, "cvr", firstNonNull(json.get("cvr"), json.get("CVR")));
        putIfValidDouble(snapshot, "avgOrderValue", firstNonNull(json.get("avgOrderValue"), json.get("aov"), json.get("客单价")));
        putIfValidDouble(snapshot, "repurchaseRate", firstNonNull(json.get("repurchaseRate"), json.get("repurchase_rate"), json.get("复购率")));
        putIfValidDouble(snapshot, "retention7d", firstNonNull(json.get("retention7d"), json.get("retention_7d"), json.get("7日留存")));
        putIfValidDouble(snapshot, "refundRate", firstNonNull(json.get("refundRate"), json.get("refund_rate"), json.get("退款率")));

        String eventTs = readText(firstNonNull(json.get("ts"), json.get("eventTs"), json.get("event_ts")));
        if (!StringUtils.hasText(eventTs)) {
            eventTs = nowText();
        }

        String updateTime = nowText();
        snapshot.put("ts", eventTs);
        snapshot.put("lastUpdate", updateTime);

        Map<String, String> hashPayload = toHashTextMap(snapshot);
        if (hashPayload.isEmpty()) {
            throw new IllegalArgumentException("invalid recommendation core metrics payload");
        }

        String dateKey = buildRecommendationKpiKey(statDate);
        stringRedisTemplate.opsForHash().putAll(dateKey, hashPayload);
        stringRedisTemplate.opsForHash().putAll(RECOMMENDATION_KPI_LATEST_KEY, hashPayload);
        stringRedisTemplate.opsForValue().set(RECOMMENDATION_KPI_LAST_UPDATE_KEY, updateTime);
    }

    @Override
    public Map<String, Object> getUserRealtimeSnapshot(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyMap();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("behaviorDistribution", stringRedisTemplate.opsForHash().entries(buildBehaviorKey(userId)));
        payload.put("categoryWeights", stringRedisTemplate.opsForHash().entries(buildCategoryWeightKey(userId)));
        payload.put("tags", stringRedisTemplate.opsForSet().members(buildTagKey(userId)));
        payload.put("lastUpdate", stringRedisTemplate.opsForValue().get(buildUserTimestampKey(userId)));
        payload.put("hotProducts1m", getHotProducts("1m", 10));
        payload.put("hotProducts1h", getHotProducts("1h", 10));
        payload.put("hotProducts1d", getHotProducts("1d", 10));
        return payload;
    }

    @Override
    public List<Map<String, Object>> getHotProducts(String window, long topN) {
        if (topN <= 0) {
            return Collections.emptyList();
        }
        String safeWindow = normalizeWindow(window);
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores("stream:product:hot:" + safeWindow, 0, topN - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple == null || !StringUtils.hasText(tuple.getValue())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("productId", parseLong(tuple.getValue()));
            row.put("score", tuple.getScore() == null ? 0D : tuple.getScore());
            row.put("window", safeWindow);
            result.add(row);
        }
        return result;
    }

    @Override
    public Map<String, Object> getRecommendationCoreMetrics(String statDate) {
        String safeDate = normalizeStatDate(statDate);
        String targetKey = StringUtils.hasText(safeDate)
                ? buildRecommendationKpiKey(safeDate)
                : RECOMMENDATION_KPI_LATEST_KEY;

        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(targetKey);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("statDate", readText(firstNonNull(raw.get("statDate"), safeDate)));
        payload.put("dau", parseLong(raw.get("dau")));
        payload.put("exposureCount", parseLong(raw.get("exposureCount")));
        payload.put("clickCount", parseLong(raw.get("clickCount")));
        payload.put("orderCount", parseLong(raw.get("orderCount")));
        payload.put("refundCount", parseLong(raw.get("refundCount")));
        payload.put("gmv", parseDouble(raw.get("gmv")));
        payload.put("refundAmount", parseDouble(raw.get("refundAmount")));
        payload.put("ctr", parseDouble(raw.get("ctr")));
        payload.put("cvr", parseDouble(raw.get("cvr")));
        payload.put("avgOrderValue", parseDouble(raw.get("avgOrderValue")));
        payload.put("repurchaseRate", parseDouble(raw.get("repurchaseRate")));
        payload.put("retention7d", parseDouble(raw.get("retention7d")));
        payload.put("refundRate", parseDouble(raw.get("refundRate")));
        payload.put("orderUserCount", parseLong(raw.get("orderUserCount")));
        payload.put("repurchaseUserCount", parseLong(raw.get("repurchaseUserCount")));
        payload.put("retainedUserCount", parseLong(raw.get("retainedUserCount")));
        payload.put("retentionBaseUserCount", parseLong(raw.get("retentionBaseUserCount")));
        payload.put("ts", readText(raw.get("ts")));
        payload.put("lastUpdate", readText(firstNonNull(
                raw.get("lastUpdate"),
                stringRedisTemplate.opsForValue().get(RECOMMENDATION_KPI_LAST_UPDATE_KEY))));
        return payload;
    }

    private JSONObject parseJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            throw new IllegalArgumentException("empty payload");
        }
        try {
            return JSON.parseObject(rawJson);
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid json payload", exception);
        }
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String buildBehaviorKey(Long userId) {
        return streamRealtimeProperties.getBehaviorStatsKeyPrefix() + ":" + userId;
    }

    private String buildCategoryWeightKey(Long userId) {
        return streamRealtimeProperties.getCategoryWeightKeyPrefix() + ":" + userId;
    }

    private String buildTagKey(Long userId) {
        return streamRealtimeProperties.getTagSetKeyPrefix() + ":" + userId;
    }

    private String buildUserTimestampKey(Long userId) {
        return "stream:user:feature:lastUpdate:" + userId;
    }

    private String buildRecommendationKpiKey(String statDate) {
        return RECOMMENDATION_KPI_KEY_PREFIX + ":" + statDate;
    }

    private void touchUserFeatureTimestamp(Long userId, String source) {
        stringRedisTemplate.opsForValue().set(buildUserTimestampKey(userId), nowText() + " [" + source + "]");
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private String readText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(String.valueOf(raw).trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private Double parseDouble(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private Set<String> parseTags(Object raw) {
        if (raw == null) {
            return Collections.emptySet();
        }

        Set<String> tags = new LinkedHashSet<>();
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (Object item : array) {
                String text = readText(item);
                if (StringUtils.hasText(text)) {
                    tags.add(text);
                }
            }
            return tags;
        }

        if (raw instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) raw) {
                String text = readText(item);
                if (StringUtils.hasText(text)) {
                    tags.add(text);
                }
            }
            return tags;
        }

        String text = readText(raw);
        if (!StringUtils.hasText(text)) {
            return Collections.emptySet();
        }
        text = text.replace("[", "").replace("]", "").replace("\"", "");
        String[] parts = text.split(",");
        for (String part : parts) {
            String tag = part == null ? "" : part.trim();
            if (StringUtils.hasText(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private String normalizeWindow(String window) {
        if (!StringUtils.hasText(window)) {
            return "1h";
        }
        String normalized = window.trim().toLowerCase();
        if ("1m".equals(normalized) || "1h".equals(normalized) || "1d".equals(normalized)) {
            return normalized;
        }
        return "1h";
    }

    private String normalizeStatDate(String statDate) {
        if (!StringUtils.hasText(statDate)) {
            return "";
        }
        String normalized = statDate.trim();
        if (normalized.length() >= 10) {
            String candidate = normalized.substring(0, 10);
            try {
                LocalDate.parse(candidate);
                return candidate;
            } catch (Exception ignore) {
                return "";
            }
        }
        try {
            return LocalDate.parse(normalized).toString();
        } catch (Exception ignore) {
            return "";
        }
    }

    private void putIfValidLong(Map<String, Object> target, String field, Object raw) {
        if (target == null || !StringUtils.hasText(field)) {
            return;
        }
        Long value = parseLong(raw);
        if (value != null && value >= 0L) {
            target.put(field, value);
        }
    }

    private void putIfValidDouble(Map<String, Object> target, String field, Object raw) {
        if (target == null || !StringUtils.hasText(field)) {
            return;
        }
        Double value = parseDouble(raw);
        if (value != null && value >= 0D) {
            target.put(field, value);
        }
    }

    private Map<String, String> toHashTextMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> payload = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            payload.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return payload;
    }
}
