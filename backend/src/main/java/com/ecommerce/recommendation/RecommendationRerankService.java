package com.ecommerce.recommendation;

import com.ecommerce.entity.Product;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class RecommendationRerankService {

    public List<Product> rankByClusterContext(List<Product> candidates, RankingContext context, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        int safeLimit = Math.min(Math.max(limit, 1), candidates.size());
        if (context == null
                || ((!context.hasCategorySignal() && !context.hasTagSignal())
                && context.getAvgPricePerOrder() == null)) {
            return new ArrayList<>(candidates.subList(0, safeLimit));
        }

        Map<String, Integer> categoryWeights = buildPreferenceWeightMap(context.getTopCategories());
        Map<String, Integer> tagWeights = buildPreferenceWeightMap(context.getTopTags());
        double targetPrice = context.getAvgPricePerOrder() == null
                ? 0.0
                : context.getAvgPricePerOrder().doubleValue();

        List<ProductScore> scoredProducts = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            Product product = candidates.get(index);
            double rankScore = (double) (candidates.size() - index) / Math.max(candidates.size(), 1);
            double score = rankScore * 55.0;

            String categoryKey = normalizeText(product.getCategoryName());
            if (!categoryWeights.isEmpty() && categoryWeights.containsKey(categoryKey)) {
                score += categoryWeights.get(categoryKey) * 30.0;
            }

            if (!tagWeights.isEmpty() && product.getTags() != null) {
                for (String tag : product.getTags()) {
                    Integer tagWeight = tagWeights.get(normalizeText(tag));
                    if (tagWeight != null) {
                        score += tagWeight * 8.0;
                    }
                }
            }

            if (targetPrice > 0 && product.getPrice() != null) {
                double priceRatio = Math.abs(product.getPrice().doubleValue() - targetPrice)
                        / Math.max(targetPrice, 1.0);
                score += Math.max(0.0, 1.0 - Math.min(priceRatio, 1.0)) * 8.0;
            }

            if (product.getSalesCount() != null && product.getSalesCount() > 0) {
                score += Math.log1p(product.getSalesCount()) * 0.55;
            }
            if (product.getRating() != null) {
                score += product.getRating().doubleValue() * 0.8;
            }

            scoredProducts.add(new ProductScore(product, score));
        }

        scoredProducts.sort((left, right) -> {
            int scoreCompare = Double.compare(right.score, left.score);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int salesCompare = Integer.compare(
                    safeInt(right.product.getSalesCount()),
                    safeInt(left.product.getSalesCount()));
            if (salesCompare != 0) {
                return salesCompare;
            }
            int ratingCompare = compareDecimal(right.product.getRating(), left.product.getRating());
            if (ratingCompare != 0) {
                return ratingCompare;
            }
            return Long.compare(
                    right.product.getId() == null ? 0L : right.product.getId(),
                    left.product.getId() == null ? 0L : left.product.getId());
        });

        List<Product> rankedProducts = new ArrayList<>();
        for (int index = 0; index < safeLimit && index < scoredProducts.size(); index++) {
            rankedProducts.add(scoredProducts.get(index).product);
        }
        return rankedProducts;
    }

    public List<Product> ensureCategoryDiversity(List<Product> candidates, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        if (candidates.size() <= limit && candidates.size() <= 3) {
            return candidates;
        }

        int preserveCount = Math.max(3, (int) (limit * 0.4));
        List<Product> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (int i = 0; i < Math.min(preserveCount, candidates.size()); i++) {
            Product product = candidates.get(i);
            if (product != null && seen.add(product.getId())) {
                result.add(product);
            }
        }

        List<Product> remaining = new ArrayList<>();
        for (int i = preserveCount; i < candidates.size(); i++) {
            Product product = candidates.get(i);
            if (product != null && !seen.contains(product.getId())) {
                remaining.add(product);
            }
        }

        Map<Long, List<Product>> byCategory = new LinkedHashMap<>();
        for (Product product : remaining) {
            Long categoryId = product.getCategoryId() != null ? product.getCategoryId() : 0L;
            byCategory.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(product);
        }

        boolean hasMore = true;
        while (result.size() < limit && hasMore) {
            hasMore = false;
            for (List<Product> categoryList : byCategory.values()) {
                if (categoryList.isEmpty()) {
                    continue;
                }
                hasMore = true;
                Product product = categoryList.remove(0);
                if (seen.add(product.getId())) {
                    result.add(product);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
        }

        return result;
    }

    public List<Product> applySceneGuardrails(List<Product> candidates,
                                              int limit,
                                              GuardrailConfig guardrailConfig,
                                              Supplier<List<Product>> supplementSupplier) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        GuardrailConfig safeConfig = guardrailConfig == null ? GuardrailConfig.defaults() : guardrailConfig;
        int safeLimit = Math.max(1, limit);
        int maxPerCategory = Math.max(1, safeConfig.getMaxPerCategory());
        int maxPerMerchant = Math.max(1, safeConfig.getMaxPerMerchant());
        int maxPerNearDuplicate = Math.max(1, safeConfig.getMaxPerNearDuplicate());
        int strictWindowSize = Math.max(1, safeConfig.getStrictWindowSize());
        int supplementMultiplier = Math.max(2, safeConfig.getSupplementMultiplier());

        LinkedHashMap<Long, Product> uniqueByProduct = new LinkedHashMap<>();
        for (Product product : candidates) {
            if (product == null || product.getId() == null) {
                continue;
            }
            uniqueByProduct.putIfAbsent(product.getId(), product);
        }
        List<Product> source = new ArrayList<>(uniqueByProduct.values());
        if (source.isEmpty()) {
            return Collections.emptyList();
        }

        int originalDistinctCategoryCount = (int) source.stream()
                .map(this::resolveCategoryKey)
                .distinct()
                .count();
        int targetCategoryCoverage = Math.max(2, Math.min(6, safeLimit / 2));
        if (source.size() < safeLimit * 2 || originalDistinctCategoryCount < targetCategoryCoverage) {
            int supplementLimit = Math.max(safeLimit * supplementMultiplier, 20);
            List<Product> supplement = supplementSupplier == null ? Collections.emptyList() : supplementSupplier.get();
            source = appendFallbackProducts(source, supplement, supplementLimit);
        }

        safeLimit = Math.min(safeLimit, source.size());
        if (source.size() <= 2) {
            return source.subList(0, Math.min(safeLimit, source.size()));
        }

        List<Product> result = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        Set<String> usedCategories = new HashSet<>();
        Map<String, Integer> merchantCounter = new HashMap<>();
        Map<String, Integer> categoryCounter = new HashMap<>();
        Map<String, Integer> nearDuplicateCounter = new HashMap<>();

        int strictWindow = Math.min(safeLimit, strictWindowSize);
        for (Product product : source) {
            if (result.size() >= strictWindow) {
                break;
            }
            if (!selectedIds.add(product.getId())) {
                continue;
            }
            String categoryKey = resolveCategoryKey(product);
            String merchantKey = resolveMerchantKey(product);
            String nearDuplicateKey = resolveNearDuplicateKey(product);
            int merchantCount = merchantCounter.getOrDefault(merchantKey, 0);
            int nearDuplicateCount = nearDuplicateCounter.getOrDefault(nearDuplicateKey, 0);
            if (merchantCount >= maxPerMerchant
                    || usedCategories.contains(categoryKey)
                    || nearDuplicateCount >= maxPerNearDuplicate) {
                selectedIds.remove(product.getId());
                continue;
            }
            usedCategories.add(categoryKey);
            merchantCounter.put(merchantKey, merchantCount + 1);
            categoryCounter.put(categoryKey, categoryCounter.getOrDefault(categoryKey, 0) + 1);
            nearDuplicateCounter.put(nearDuplicateKey, nearDuplicateCount + 1);
            result.add(product);
        }

        for (Product product : source) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (!selectedIds.add(product.getId())) {
                continue;
            }
            String categoryKey = resolveCategoryKey(product);
            String merchantKey = resolveMerchantKey(product);
            String nearDuplicateKey = resolveNearDuplicateKey(product);
            int merchantCount = merchantCounter.getOrDefault(merchantKey, 0);
            int categoryCount = categoryCounter.getOrDefault(categoryKey, 0);
            int nearDuplicateCount = nearDuplicateCounter.getOrDefault(nearDuplicateKey, 0);
            if (merchantCount >= maxPerMerchant
                    || categoryCount >= maxPerCategory
                    || nearDuplicateCount >= maxPerNearDuplicate) {
                selectedIds.remove(product.getId());
                continue;
            }
            merchantCounter.put(merchantKey, merchantCount + 1);
            categoryCounter.put(categoryKey, categoryCount + 1);
            nearDuplicateCounter.put(nearDuplicateKey, nearDuplicateCount + 1);
            result.add(product);
        }

        for (Product product : source) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (!selectedIds.add(product.getId())) {
                continue;
            }
            String merchantKey = resolveMerchantKey(product);
            String nearDuplicateKey = resolveNearDuplicateKey(product);
            int merchantCount = merchantCounter.getOrDefault(merchantKey, 0);
            int nearDuplicateCount = nearDuplicateCounter.getOrDefault(nearDuplicateKey, 0);
            if (merchantCount >= maxPerMerchant || nearDuplicateCount >= maxPerNearDuplicate) {
                selectedIds.remove(product.getId());
                continue;
            }
            merchantCounter.put(merchantKey, merchantCount + 1);
            nearDuplicateCounter.put(nearDuplicateKey, nearDuplicateCount + 1);
            result.add(product);
        }

        for (Product product : source) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (!selectedIds.add(product.getId())) {
                continue;
            }
            String nearDuplicateKey = resolveNearDuplicateKey(product);
            int nearDuplicateCount = nearDuplicateCounter.getOrDefault(nearDuplicateKey, 0);
            if (nearDuplicateCount >= maxPerNearDuplicate + 1) {
                selectedIds.remove(product.getId());
                continue;
            }
            nearDuplicateCounter.put(nearDuplicateKey, nearDuplicateCount + 1);
            result.add(product);
        }

        for (Product product : source) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (selectedIds.add(product.getId())) {
                result.add(product);
            }
        }
        return result;
    }

    private List<Product> appendFallbackProducts(List<Product> baseProducts, List<Product> fallbackProducts, int limit) {
        int safeLimit = Math.max(1, limit);
        List<Product> result = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        if (baseProducts != null) {
            for (Product product : baseProducts) {
                if (product == null || product.getId() == null) {
                    continue;
                }
                if (selectedIds.add(product.getId())) {
                    result.add(product);
                    if (result.size() >= safeLimit) {
                        return result;
                    }
                }
            }
        }
        if (fallbackProducts != null) {
            for (Product product : fallbackProducts) {
                if (product == null || product.getId() == null) {
                    continue;
                }
                if (selectedIds.add(product.getId())) {
                    result.add(product);
                    if (result.size() >= safeLimit) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Integer> buildPreferenceWeightMap(List<String> rawValues) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return weights;
        }
        int weight = rawValues.size();
        for (String rawValue : rawValues) {
            String normalized = normalizeText(rawValue);
            if (!normalized.isEmpty()) {
                weights.putIfAbsent(normalized, weight);
                weight = Math.max(1, weight - 1);
            }
        }
        return weights;
    }

    private String resolveNearDuplicateKey(Product product) {
        if (product == null) {
            return "duplicate:unknown";
        }
        String categoryKey = resolveCategoryKey(product);
        String titleKey = normalizeProductNameForDuplicate(product.getName());
        String imageKey = normalizeImageForDuplicate(product.getImage());
        if (titleKey.isEmpty() && imageKey.isEmpty()) {
            return "duplicate:fallback:" + (product.getId() == null ? 0L : product.getId());
        }
        return categoryKey + "|title:" + titleKey + "|image:" + imageKey;
    }

    private String normalizeProductNameForDuplicate(String productName) {
        String normalized = normalizeText(productName);
        if (normalized.isEmpty()) {
            return "";
        }

        String stripped = normalized
                .replaceAll("(?i)\\b(20\\d{2}|[vx]?\\d+[a-z0-9\\-]*)\\b", " ")
                .replaceAll("(?i)(官方|旗舰|正品|新品|热卖|包邮|同款|轻享版|升级版)", " ")
                .replaceAll("[\\p{Punct}\\s]+", " ")
                .trim();

        String hanOnly = stripped.replaceAll("[^\\p{IsHan}]", "");
        if (hanOnly.length() >= 4) {
            return hanOnly.length() > 14 ? hanOnly.substring(0, 14) : hanOnly;
        }

        String compact = stripped.replaceAll("\\s+", "");
        if (compact.length() > 18) {
            return compact.substring(0, 18);
        }
        return compact;
    }

    private String normalizeImageForDuplicate(String imageUrl) {
        String normalized = normalizeText(imageUrl);
        if (normalized.isEmpty()) {
            return "";
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < normalized.length()) {
            normalized = normalized.substring(slashIndex + 1);
        }
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex > 0) {
            normalized = normalized.substring(0, dotIndex);
        }
        normalized = normalized
                .replaceAll("\\d+", "")
                .replaceAll("[^a-z\\p{IsHan}]", "");
        if (normalized.length() > 18) {
            return normalized.substring(0, 18);
        }
        return normalized;
    }

    private String resolveCategoryKey(Product product) {
        if (product == null) {
            return "category:unknown";
        }
        if (product.getCategoryId() != null) {
            return "category:id:" + product.getCategoryId();
        }
        String categoryName = normalizeText(product.getCategoryName());
        if (!categoryName.isEmpty()) {
            return "category:name:" + categoryName;
        }
        Long productId = product.getId() == null ? 0L : product.getId();
        return "category:unknown:" + productId;
    }

    private String resolveMerchantKey(Product product) {
        if (product == null) {
            return "merchant:unknown";
        }
        if (product.getMerchantId() != null) {
            return "merchant:id:" + product.getMerchantId();
        }
        String merchantName = normalizeText(product.getMerchantName());
        if (!merchantName.isEmpty()) {
            return "merchant:name:" + merchantName;
        }
        Long productId = product.getId() == null ? 0L : product.getId();
        return "merchant:unknown:" + productId;
    }

    private String normalizeText(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "";
        }
        return rawValue.trim().toLowerCase(Locale.ROOT);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int compareDecimal(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    public static class RankingContext {
        private final List<String> topCategories;
        private final List<String> topTags;
        private final BigDecimal avgPricePerOrder;

        public RankingContext(List<String> topCategories, List<String> topTags, BigDecimal avgPricePerOrder) {
            this.topCategories = topCategories == null ? Collections.emptyList() : topCategories;
            this.topTags = topTags == null ? Collections.emptyList() : topTags;
            this.avgPricePerOrder = avgPricePerOrder;
        }

        public List<String> getTopCategories() {
            return topCategories;
        }

        public List<String> getTopTags() {
            return topTags;
        }

        public BigDecimal getAvgPricePerOrder() {
            return avgPricePerOrder;
        }

        public boolean hasCategorySignal() {
            return !topCategories.isEmpty();
        }

        public boolean hasTagSignal() {
            return !topTags.isEmpty();
        }
    }

    public static class GuardrailConfig {
        private final int maxPerCategory;
        private final int maxPerMerchant;
        private final int maxPerNearDuplicate;
        private final int strictWindowSize;
        private final int supplementMultiplier;

        public GuardrailConfig(int maxPerCategory,
                               int maxPerMerchant,
                               int maxPerNearDuplicate,
                               int strictWindowSize,
                               int supplementMultiplier) {
            this.maxPerCategory = maxPerCategory;
            this.maxPerMerchant = maxPerMerchant;
            this.maxPerNearDuplicate = maxPerNearDuplicate;
            this.strictWindowSize = strictWindowSize;
            this.supplementMultiplier = supplementMultiplier;
        }

        public static GuardrailConfig defaults() {
            return new GuardrailConfig(2, 2, 1, 8, 4);
        }

        public int getMaxPerCategory() {
            return maxPerCategory;
        }

        public int getMaxPerMerchant() {
            return maxPerMerchant;
        }

        public int getMaxPerNearDuplicate() {
            return maxPerNearDuplicate;
        }

        public int getStrictWindowSize() {
            return strictWindowSize;
        }

        public int getSupplementMultiplier() {
            return supplementMultiplier;
        }
    }

    private static class ProductScore {
        private final Product product;
        private final double score;

        private ProductScore(Product product, double score) {
            this.product = product;
            this.score = score;
        }
    }
}
