package com.ecommerce.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserPreference;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.mapper.UserPreferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户画像初始化/自愈服务：
 * - 用户画像缺失或无效时，自动生成多品类画像，避免推荐退化为单一热门类目。
 * - 支持全量用户批量初始化（管理端可调用）。
 */
@Component
public class UserPreferenceBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceBootstrapService.class);
    private static final int MAX_CATEGORY_PREF_SIZE = 5;
    private static final int MAX_TAG_PREF_SIZE = 8;

    @Autowired
    private UserPreferenceMapper userPreferenceMapper;

    @Autowired
    private UserBehaviorMapper userBehaviorMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    public UserPreference ensureUserPreferenceInitialized(Long userId) {
        return ensureUserPreferenceInitialized(userId, false);
    }

    public UserPreference ensureUserPreferenceInitialized(Long userId, boolean forceRebuild) {
        if (userId == null || userId <= 0) {
            return null;
        }
        UserPreference existing = loadByUserId(userId);
        Set<Long> validCategoryIds = loadValidCategoryIds();
        if (!forceRebuild && !needsBootstrap(existing, validCategoryIds)) {
            return existing;
        }
        UserPreference rebuilt = buildAndPersistPreference(userId, existing, validCategoryIds);
        return rebuilt != null ? rebuilt : existing;
    }

    public Map<String, Object> bootstrapAllUsers(boolean forceRebuild, int limit) {
        int safeLimit = limit <= 0 ? 0 : Math.min(limit, 50_000);
        Set<Long> validCategoryIds = loadValidCategoryIds();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(User::getId);
        wrapper.eq(User::getRole, Constants.Role.USER);
        wrapper.and(w -> w.isNull(User::getStatus).or().eq(User::getStatus, 1));
        wrapper.orderByAsc(User::getId);
        if (safeLimit > 0) {
            wrapper.last("LIMIT " + safeLimit);
        }

        List<User> users = userMapper.selectList(wrapper);
        int total = users == null ? 0 : users.size();
        int rebuilt = 0;
        int skipped = 0;
        int failed = 0;
        List<Long> failedUserIds = new ArrayList<>();

        if (users != null) {
            for (User user : users) {
                if (user == null || user.getId() == null) {
                    continue;
                }
                try {
                    UserPreference before = loadByUserId(user.getId());
                    boolean shouldRebuild = forceRebuild || needsBootstrap(before, validCategoryIds);
                    if (!shouldRebuild) {
                        skipped++;
                        continue;
                    }
                    UserPreference after = buildAndPersistPreference(user.getId(), before, validCategoryIds);
                    if (after != null) {
                        rebuilt++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    failed++;
                    failedUserIds.add(user.getId());
                    log.warn("[PreferenceBootstrap] rebuild failed for userId={}: {}", user.getId(), ex.getMessage());
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalUsers", total);
        summary.put("rebuilt", rebuilt);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        summary.put("failedUserIds", failedUserIds);
        summary.put("forceRebuild", forceRebuild);
        summary.put("executedAt", LocalDateTime.now());
        return summary;
    }

    private UserPreference loadByUserId(Long userId) {
        return userPreferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, userId)
                        .last("LIMIT 1"));
    }

    private Set<Long> loadValidCategoryIds() {
        List<Map<String, Object>> categoryRows = productMapper.selectAllCategoryIds();
        if (categoryRows == null || categoryRows.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> categoryIds = new LinkedHashSet<>();
        for (Map<String, Object> row : categoryRows) {
            Long categoryId = parseLong(row.get("categoryId"));
            if (categoryId != null) {
                categoryIds.add(categoryId);
            }
        }
        return categoryIds;
    }

    private boolean needsBootstrap(UserPreference pref, Set<Long> validCategoryIds) {
        if (pref == null) {
            return true;
        }
        if (isLegacySyntheticPreference(pref)) {
            return true;
        }
        Map<String, Integer> categoryPrefs = pref.getCategoryPreferences();
        if (categoryPrefs == null || categoryPrefs.isEmpty()) {
            return true;
        }
        int positiveCount = 0;
        int validCount = 0;
        for (Map.Entry<String, Integer> entry : categoryPrefs.entrySet()) {
            Integer score = entry.getValue();
            if (score == null || score <= 0) {
                continue;
            }
            positiveCount++;
            Long categoryId = parseLong(entry.getKey());
            if (categoryId != null && (validCategoryIds.isEmpty() || validCategoryIds.contains(categoryId))) {
                validCount++;
            }
        }
        if (positiveCount == 0 || validCount == 0) {
            return true;
        }
        return false;
    }

    private boolean isLegacySyntheticPreference(UserPreference pref) {
        if (pref == null || pref.getPriceRangeMin() == null || pref.getPriceRangeMax() == null) {
            return false;
        }
        return pref.getPriceRangeMin().compareTo(new BigDecimal("29.00")) == 0
                && pref.getPriceRangeMax().compareTo(new BigDecimal("3999.00")) == 0;
    }

    private UserPreference buildAndPersistPreference(Long userId,
                                                     UserPreference existing,
                                                     Set<Long> validCategoryIds) {
        List<Long> orderedCategories = new ArrayList<>(validCategoryIds);
        if (orderedCategories.isEmpty()) {
            return existing;
        }

        Map<Long, Double> categoryScores = buildCategoryScores(userId, orderedCategories);
        Map<String, Double> tagScores = new LinkedHashMap<>();
        List<Map<String, Object>> behaviorPreferences = userBehaviorMapper.selectUserPreferences(userId);
        Set<Long> interactedProductIds = new LinkedHashSet<>();

        if (behaviorPreferences != null) {
            for (Map<String, Object> row : behaviorPreferences) {
                Long categoryId = parseLong(row.get("category_id"));
                double weight = parseDouble(row.get("weight"));
                if (categoryId != null && weight > 0D && (validCategoryIds.isEmpty() || validCategoryIds.contains(categoryId))) {
                    categoryScores.merge(categoryId, weight * 14D, Double::sum);
                }

                Long productId = parseLong(row.get("product_id"));
                if (productId != null) {
                    interactedProductIds.add(productId);
                }

                mergeTagScores(tagScores, parseTags(row.get("tags")), Math.max(1D, weight));
            }
        }

        Map<String, Integer> categoryPreferences = toCategoryPreferences(categoryScores, orderedCategories);
        if (categoryPreferences.isEmpty()) {
            if (existing != null && existing.getCategoryPreferences() != null && !existing.getCategoryPreferences().isEmpty()) {
                existing.setCategoryPreferences(Collections.emptyMap());
                existing.setTagPreferences(Collections.emptyMap());
                existing.setPriceRangeMin(null);
                existing.setPriceRangeMax(null);
                savePreference(userId, existing);
            }
            return existing;
        }

        Map<String, Integer> tagPreferences = toTagPreferences(tagScores);

        PriceRange priceRange = resolvePriceRange(interactedProductIds, categoryPreferences.keySet());

        UserPreference target = existing == null ? new UserPreference() : existing;
        target.setUserId(userId);
        target.setCategoryPreferences(categoryPreferences);
        target.setTagPreferences(tagPreferences);
        target.setPriceRangeMin(priceRange.min);
        target.setPriceRangeMax(priceRange.max);

        savePreference(userId, target);
        return target;
    }

    private void savePreference(Long userId, UserPreference target) {
        if (target == null) {
            return;
        }
        if (target.getId() != null) {
            userPreferenceMapper.updateById(target);
            return;
        }

        try {
            userPreferenceMapper.insert(target);
        } catch (DuplicateKeyException ex) {
            UserPreference current = loadByUserId(userId);
            if (current == null || current.getId() == null) {
                throw ex;
            }
            target.setId(current.getId());
            userPreferenceMapper.updateById(target);
            log.info("[PreferenceBootstrap] user preference already existed, updated instead. userId={}", userId);
        }
    }

    private Map<Long, Double> buildCategoryScores(Long userId, List<Long> categoryIds) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        if (userId == null || userId <= 0) {
            return scores;
        }
        Set<Long> validCategoryIds = categoryIds == null
                ? Collections.emptySet()
                : categoryIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));

        try {
            List<Map<String, Object>> searchPreferences =
                    userBehaviorMapper.selectUserSearchCategoryPreferences(userId, Math.max(MAX_CATEGORY_PREF_SIZE * 3, 12));
            if (searchPreferences == null || searchPreferences.isEmpty()) {
                return scores;
            }
            for (Map<String, Object> row : searchPreferences) {
                Long categoryId = parseLong(row.get("category_id"));
                double weight = parseDouble(row.get("weight"));
                if (categoryId == null || weight <= 0D) {
                    continue;
                }
                if (!validCategoryIds.isEmpty() && !validCategoryIds.contains(categoryId)) {
                    continue;
                }
                scores.merge(categoryId, weight * 10D, Double::sum);
            }
        } catch (Exception exception) {
            log.debug("[PreferenceBootstrap] search category preference unavailable userId={}: {}",
                    userId,
                    exception.getMessage());
        }
        return scores;
    }

    private Map<String, Integer> toCategoryPreferences(Map<Long, Double> scores, List<Long> fallbackCategories) {
        if (scores == null || scores.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map.Entry<Long, Double>> ordered = scores.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0D)
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .collect(Collectors.toList());

        if (ordered.isEmpty()) {
            return Collections.emptyMap();
        }

        double maxScore = Math.max(ordered.get(0).getValue(), 1D);
        Map<String, Integer> result = new LinkedHashMap<>();
        int keep = Math.min(MAX_CATEGORY_PREF_SIZE, ordered.size());
        for (int i = 0; i < keep; i++) {
            Map.Entry<Long, Double> entry = ordered.get(i);
            int normalized = (int) Math.round((entry.getValue() / maxScore) * 100D);
            normalized = Math.max(8, Math.min(100, normalized));
            result.put(String.valueOf(entry.getKey()), normalized);
        }
        return result;
    }

    private Map<String, Integer> toTagPreferences(Map<String, Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map.Entry<String, Double>> ordered = scores.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().trim().isEmpty())
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0D)
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .collect(Collectors.toList());
        if (ordered.isEmpty()) {
            return Collections.emptyMap();
        }

        double maxScore = Math.max(ordered.get(0).getValue(), 1D);
        Map<String, Integer> result = new LinkedHashMap<>();
        int keep = Math.min(MAX_TAG_PREF_SIZE, ordered.size());
        for (int i = 0; i < keep; i++) {
            Map.Entry<String, Double> entry = ordered.get(i);
            int normalized = (int) Math.round((entry.getValue() / maxScore) * 100D);
            normalized = Math.max(6, Math.min(100, normalized));
            result.put(entry.getKey().trim(), normalized);
        }
        return result;
    }

    private PriceRange resolvePriceRange(Set<Long> interactedProductIds, Collection<String> categoryIds) {
        List<BigDecimal> prices = new ArrayList<>();
        if (interactedProductIds != null && !interactedProductIds.isEmpty()) {
            List<Long> ids = interactedProductIds.stream().limit(200).collect(Collectors.toList());
            List<Product> products = productMapper.selectByIds(ids);
            appendValidPrices(prices, products);
        }

        if (prices.isEmpty()) {
            return new PriceRange(null, null);
        }

        List<BigDecimal> sorted = prices.stream()
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        if (sorted.isEmpty()) {
            return new PriceRange(null, null);
        }

        BigDecimal p20 = percentile(sorted, 0.20D);
        BigDecimal p80 = percentile(sorted, 0.80D);
        BigDecimal min = p20 == null ? sorted.get(0) : p20;
        BigDecimal max = p80 == null ? sorted.get(sorted.size() - 1) : p80;
        if (max.compareTo(min) < 0) {
            BigDecimal swap = min;
            min = max;
            max = swap;
        }
        if (max.subtract(min).compareTo(new BigDecimal("20")) < 0) {
            max = min.add(new BigDecimal("20"));
        }
        min = min.max(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
        max = max.max(min).setScale(2, RoundingMode.HALF_UP);
        return new PriceRange(min, max);
    }

    private void appendValidPrices(List<BigDecimal> bucket, List<Product> products) {
        if (bucket == null || products == null || products.isEmpty()) {
            return;
        }
        for (Product product : products) {
            if (product == null || product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            bucket.add(product.getPrice());
        }
    }

    private BigDecimal percentile(List<BigDecimal> sorted, double percentile) {
        if (sorted == null || sorted.isEmpty()) {
            return null;
        }
        double clamped = Math.max(0D, Math.min(1D, percentile));
        int index = (int) Math.floor((sorted.size() - 1) * clamped);
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private void mergeTagScores(Map<String, Double> target, Collection<String> tags, double weight) {
        if (target == null || tags == null || tags.isEmpty()) {
            return;
        }
        double safeWeight = Math.max(0.5D, weight);
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String normalized = tag.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            target.merge(normalized, safeWeight, Double::sum);
        }
    }

    private Collection<String> collectFallbackTags(Collection<String> preferredCategoryIds) {
        List<Product> hotProducts = productMapper.selectHotProducts(120);
        if (hotProducts == null || hotProducts.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> preferred = preferredCategoryIds == null
                ? Collections.emptySet()
                : preferredCategoryIds.stream().map(this::parseLong).filter(Objects::nonNull).collect(Collectors.toSet());

        Deque<String> tags = new ArrayDeque<>();
        for (Product product : hotProducts) {
            if (product == null || product.getTags() == null || product.getTags().isEmpty()) {
                continue;
            }
            if (!preferred.isEmpty() && product.getCategoryId() != null && !preferred.contains(product.getCategoryId())) {
                continue;
            }
            for (String tag : product.getTags()) {
                if (tag == null || tag.trim().isEmpty()) {
                    continue;
                }
                tags.add(tag.trim());
                if (tags.size() >= 20) {
                    return new ArrayList<>(tags);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private List<String> parseTags(Object rawTags) {
        if (rawTags == null) {
            return Collections.emptyList();
        }
        if (rawTags instanceof Collection<?>) {
            List<String> tags = new ArrayList<>();
            for (Object item : (Collection<?>) rawTags) {
                if (item != null) {
                    String text = String.valueOf(item).trim();
                    if (!text.isEmpty()) {
                        tags.add(text);
                    }
                }
            }
            return tags;
        }

        String raw = String.valueOf(rawTags).trim();
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        if (raw.startsWith("[") && raw.endsWith("]")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        String[] parts = raw.split(",");
        List<String> tags = new ArrayList<>(parts.length);
        for (String part : parts) {
            String normalized = part == null ? "" : part.trim().replace("\"", "").replace("'", "");
            if (!normalized.isEmpty()) {
                tags.add(normalized);
            }
        }
        return tags;
    }

    private Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private double parseDouble(Object raw) {
        if (raw == null) {
            return 0D;
        }
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private static final class PriceRange {
        private final BigDecimal min;
        private final BigDecimal max;

        private PriceRange(BigDecimal min, BigDecimal max) {
            this.min = min;
            this.max = max;
        }
    }
}
