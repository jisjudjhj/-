package com.ecommerce.service;

import com.ecommerce.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SearchQualityMetricsService {

    private static final String QUERY_TOTAL_KEY = "search:quality:query:total";
    private static final String QUERY_CORRECTED_KEY = "search:quality:query:corrected";
    private static final String DAILY_PREFIX = "search:quality:query:daily:";
    private static final DateTimeFormatter DAY_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ISO_DATE;

    @Autowired
    private RedisUtil redisUtil;

    public void recordQuery(boolean correctedApplied) {
        incrementWithExpire(QUERY_TOTAL_KEY, 180, TimeUnit.DAYS);
        if (correctedApplied) {
            incrementWithExpire(QUERY_CORRECTED_KEY, 180, TimeUnit.DAYS);
        }

        String dayKey = LocalDate.now().format(DAY_KEY_FORMATTER);
        incrementWithExpire(DAILY_PREFIX + dayKey + ":total", 120, TimeUnit.DAYS);
        if (correctedApplied) {
            incrementWithExpire(DAILY_PREFIX + dayKey + ":corrected", 120, TimeUnit.DAYS);
        }
    }

    public Map<String, Object> getSummary() {
        long totalQueries = readLong(redisUtil.get(QUERY_TOTAL_KEY));
        long correctedHits = readLong(redisUtil.get(QUERY_CORRECTED_KEY));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalQueries", totalQueries);
        summary.put("correctedHits", correctedHits);
        summary.put("correctionHitRate", percentage(correctedHits, totalQueries));
        return summary;
    }

    public List<Map<String, Object>> getRecentDailyMetrics(int days) {
        int safeDays = Math.max(1, Math.min(days, 60));
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = safeDays - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String dayKey = day.format(DAY_KEY_FORMATTER);
            long total = readLong(redisUtil.get(DAILY_PREFIX + dayKey + ":total"));
            long corrected = readLong(redisUtil.get(DAILY_PREFIX + dayKey + ":corrected"));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.format(DAY_LABEL_FORMATTER));
            row.put("totalQueries", total);
            row.put("correctedHits", corrected);
            row.put("correctionHitRate", percentage(corrected, total));
            result.add(row);
        }
        return result;
    }

    private void incrementWithExpire(String key, long timeout, TimeUnit unit) {
        Long count = redisUtil.incr(key, 1);
        if (count != null && count == 1L) {
            redisUtil.expire(key, timeout, unit);
        }
    }

    private long readLong(Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0D;
        }
        return Math.round(((double) numerator * 10000D) / (double) denominator) / 100D;
    }
}
