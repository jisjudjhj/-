package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.Product;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.service.RedisStockService;
import com.ecommerce.service.ProductService;
import com.ecommerce.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 50;
    private static final int DEFAULT_TOP_PRODUCT_LIMIT = 5;
    private static final String PRODUCT_DETAIL_CACHE_KEY_PREFIX = "cache:product:detail:";
    private static final String PRODUCT_DETAIL_LOCK_KEY_PREFIX = "cache:product:detail:lock:";
    private static final String PRODUCT_HOT_CACHE_KEY_PREFIX = "cache:product:hot:";
    private static final String PRODUCT_HOT_CACHE_VERSION_KEY = "cache:product:hot:version";
    private static final String PRODUCT_SEARCH_FACET_CACHE_KEY_PREFIX = "cache:product:search:facets:";
    private static final String PRODUCT_SEARCH_FACET_VERSION_KEY = "cache:product:search:facets:version";

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisStockService redisStockService;

    @Override
    @Transactional
    public boolean save(Product entity) {
        boolean saved = super.save(normalizeProductForPersistence(entity));
        if (saved && entity != null && entity.getId() != null) {
            evictProductCaches(Collections.singletonList(entity.getId()));
        }
        return saved;
    }

    @Override
    @Transactional
    public boolean updateById(Product entity) {
        boolean updated = super.updateById(normalizeProductForPersistence(entity));
        if (updated && entity != null && entity.getId() != null) {
            evictProductCaches(Collections.singletonList(entity.getId()));
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean removeById(Serializable id) {
        boolean removed = super.removeById(id);
        if (removed && id instanceof Long) {
            evictProductCaches(Collections.singletonList((Long) id));
        }
        return removed;
    }

    @Override
    public IPage<Product> getProductPage(int page, int size, Long categoryId, String keyword,
                                         BigDecimal minPrice, BigDecimal maxPrice,
                                         String sortField, String sortOrder) {
        LambdaQueryWrapper<Product> wrapper = buildPublicProductWrapper(categoryId, keyword, minPrice, maxPrice);

        applyPublicSort(wrapper, keyword, sortField, sortOrder);
        return this.page(new Page<>(page, size), wrapper);
    }

    @Override
    public Map<String, Object> getSearchFacets(Long categoryId, String keyword,
                                               BigDecimal minPrice, BigDecimal maxPrice) {
        long facetVersion = resolveSearchFacetCacheVersion();
        String cacheKey = buildSearchFacetCacheKey(facetVersion, categoryId, keyword, minPrice, maxPrice);
        Map<String, Object> cached = readMapCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<String> keywordParts = splitKeywordParts(keyword);
        List<Map<String, Object>> categoryBuckets = baseMapper.selectSearchCategoryBuckets(
                categoryId, keywordParts, minPrice, maxPrice);
        if (categoryBuckets == null) {
            categoryBuckets = Collections.emptyList();
        }

        Map<String, Object> priceSummary = baseMapper.selectSearchPriceBucketSummary(
                categoryId, keywordParts, minPrice, maxPrice);
        long totalMatched = getLongValue(priceSummary, "totalMatched");

        List<Map<String, Object>> priceBuckets = new ArrayList<>();
        priceBuckets.add(buildPriceBucket("0-99", getLongValue(priceSummary, "bucket0_99")));
        priceBuckets.add(buildPriceBucket("100-299", getLongValue(priceSummary, "bucket100_299")));
        priceBuckets.add(buildPriceBucket("300-999", getLongValue(priceSummary, "bucket300_999")));
        priceBuckets.add(buildPriceBucket("1000-2999", getLongValue(priceSummary, "bucket1000_2999")));
        priceBuckets.add(buildPriceBucket("3000+", getLongValue(priceSummary, "bucket3000_plus")));

        Map<String, Object> facets = new HashMap<>();
        facets.put("categoryBuckets", categoryBuckets);
        facets.put("priceBuckets", priceBuckets);
        facets.put("totalMatched", totalMatched);
        redisUtil.set(cacheKey, facets, withJitterSeconds(120, 0.25D), TimeUnit.SECONDS);
        return facets;
    }

    @Override
    public Product getProductDetail(Long id) {
        if (id == null || id <= 0) {
            return null;
        }

        String cacheKey = buildProductDetailCacheKey(id);
        String lockKey = buildProductDetailLockKey(id);
        long now = System.currentTimeMillis();

        ProductCacheEntry cached = readProductCacheEntry(cacheKey);
        if (cached != null) {
            if (cached.expireAtMillis != null && cached.expireAtMillis > now) {
                return cached.product;
            }
            if (tryLock(lockKey, 10)) {
                try {
                    refreshProductDetailCache(id, cacheKey);
                } finally {
                    unlock(lockKey);
                }
            }
            return cached.product;
        }

        if (tryLock(lockKey, 10)) {
            try {
                return refreshProductDetailCache(id, cacheKey);
            } finally {
                unlock(lockKey);
            }
        }

        try {
            Thread.sleep(40L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        ProductCacheEntry retryEntry = readProductCacheEntry(cacheKey);
        if (retryEntry != null) {
            return retryEntry.product;
        }
        return refreshProductDetailCache(id, cacheKey);
    }

    @Override
    public List<Product> getHotProducts(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        long hotCacheVersion = resolveHotCacheVersion();
        String cacheKey = PRODUCT_HOT_CACHE_KEY_PREFIX + hotCacheVersion + ":" + safeLimit;
        List<Product> cached = readHotProductsCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Product> products = baseMapper.selectHotProducts(safeLimit);
        redisUtil.setList(cacheKey, products, withJitterSeconds(180, 0.2D), TimeUnit.SECONDS);
        return products;
    }

    @Override
    public List<Product> getProductsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return baseMapper.selectByIds(ids);
    }

    @Override
    public IPage<Product> getMerchantProducts(Long merchantId, int page, int size) {
        return getMerchantProducts(merchantId, page, size, null, null, null,
                null, null, null);
    }

    @Override
    public IPage<Product> getMerchantProducts(Long merchantId, int page, int size, String keyword,
                                              Integer status, Long categoryId, String stockStatus,
                                              String sortField, String sortOrder) {
        LambdaQueryWrapper<Product> wrapper = buildMerchantProductWrapper(
                merchantId, keyword, status, categoryId, stockStatus);
        applyMerchantSort(wrapper, sortField, sortOrder);
        return this.page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public int batchUpdateMerchantProductStatus(Long merchantId, List<Long> productIds, Integer status) {
        if (!Objects.equals(status, Constants.ProductStatus.ON_SHELF)
                && !Objects.equals(status, Constants.ProductStatus.OFF_SHELF)) {
            throw new BusinessException("商品状态不合法");
        }

        List<Product> products = loadMerchantProductsOrThrow(merchantId, productIds);
        List<Product> updates = products.stream().map(product -> {
            Product update = new Product();
            update.setId(product.getId());
            update.setStatus(status);
            return update;
        }).collect(Collectors.toList());

        this.updateBatchById(updates);
        evictProductCaches(products.stream().map(Product::getId).collect(Collectors.toList()));
        return updates.size();
    }

    @Override
    @Transactional
    public int batchUpdateMerchantProductStock(Long merchantId, List<Long> productIds, String operation, Integer stock) {
        String safeOperation = StringUtils.hasText(operation) ? operation.trim().toLowerCase(Locale.ROOT) : "";
        if (!"set".equals(safeOperation) && !"increase".equals(safeOperation) && !"decrease".equals(safeOperation)) {
            throw new BusinessException("库存操作类型不合法");
        }
        if (stock == null || stock < 0) {
            throw new BusinessException("库存值不能小于 0");
        }
        if (!"set".equals(safeOperation) && stock == 0) {
            throw new BusinessException("库存变更值必须大于 0");
        }

        List<Product> products = loadMerchantProductsOrThrow(merchantId, productIds);
        List<Product> updates = new ArrayList<>(products.size());
        for (Product product : products) {
            int currentStock = product.getStock() == null ? 0 : product.getStock();
            int newStock;
            switch (safeOperation) {
                case "set":
                    newStock = stock;
                    break;
                case "increase":
                    newStock = currentStock + stock;
                    break;
                case "decrease":
                    if (currentStock < stock) {
                        throw new BusinessException("商品库存不足: " + product.getName());
                    }
                    newStock = currentStock - stock;
                    break;
                default:
                    throw new BusinessException("库存操作类型不合法");
            }
            Product update = new Product();
            update.setId(product.getId());
            update.setStock(newStock);
            updates.add(update);
        }

        this.updateBatchById(updates);
        evictProductCaches(products.stream().map(Product::getId).collect(Collectors.toList()));
        return updates.size();
    }

    @Override
    public IPage<Product> getMerchantLowStockProducts(Long merchantId, int threshold, int page, int size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getMerchantId, merchantId)
                .le(Product::getStock, threshold)
                .orderByAsc(Product::getStock)
                .orderByDesc(Product::getUpdateTime);
        return this.page(new Page<>(page, size), wrapper);
    }

    @Override
    public Map<String, Object> getMerchantProductStats(Long merchantId, int lowStockThreshold, int topLimit) {
        int safeThreshold = Math.max(lowStockThreshold, 0);
        int safeTopLimit = topLimit > 0 ? topLimit : DEFAULT_TOP_PRODUCT_LIMIT;

        Map<String, Object> summary = baseMapper.selectMerchantProductStats(merchantId, safeThreshold);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProducts", getLongValue(summary, "totalProducts"));
        stats.put("onShelfCount", getLongValue(summary, "onShelfCount"));
        stats.put("offShelfCount", getLongValue(summary, "offShelfCount"));
        stats.put("lowStockCount", getLongValue(summary, "lowStockCount"));
        stats.put("totalSales", getLongValue(summary, "totalSales"));
        stats.put("estimatedRevenue", getBigDecimalValue(summary, "estimatedRevenue"));
        stats.put("lowStockThreshold", safeThreshold);
        stats.put("topProducts", baseMapper.selectMerchantTopProducts(merchantId, safeTopLimit));
        stats.put("categorySales", baseMapper.selectMerchantCategorySalesStats(merchantId));
        return stats;
    }

    @Override
    public List<Map<String, Object>> getCategorySalesStats() {
        return baseMapper.selectCategorySalesStats();
    }

    private LambdaQueryWrapper<Product> buildMerchantProductWrapper(Long merchantId, String keyword,
                                                                    Integer status, Long categoryId,
                                                                    String stockStatus) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getMerchantId, merchantId);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Product::getName, keyword)
                    .or().like(Product::getDescription, keyword)
                    .or().apply("JSON_CONTAINS(tags, CONCAT('\"', {0}, '\"'))", keyword));
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }

        if (StringUtils.hasText(stockStatus)) {
            String safeStockStatus = stockStatus.trim().toLowerCase(Locale.ROOT);
            switch (safeStockStatus) {
                case "low":
                    wrapper.gt(Product::getStock, 0)
                            .le(Product::getStock, DEFAULT_LOW_STOCK_THRESHOLD);
                    break;
                case "empty":
                    wrapper.le(Product::getStock, 0);
                    break;
                case "normal":
                    wrapper.gt(Product::getStock, DEFAULT_LOW_STOCK_THRESHOLD);
                    break;
                default:
                    throw new BusinessException("库存筛选类型不支持");
            }
        }
        return wrapper;
    }

    private LambdaQueryWrapper<Product> buildPublicProductWrapper(Long categoryId, String keyword,
                                                                  BigDecimal minPrice, BigDecimal maxPrice) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, Constants.ProductStatus.ON_SHELF);
        wrapper.apply("NOT EXISTS (" +
                "SELECT 1 FROM seckill_activity_apply saa " +
                "JOIN seckill_activity sa ON sa.id = saa.activity_id " +
                "WHERE saa.product_id = product.id " +
                "AND saa.audit_status = 1 " +
                "AND sa.publish_status = 1 " +
                "AND sa.end_time > CURRENT_TIMESTAMP" +
                ")");

        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            String[] parts = trimmed.split("[\\s,，]+");
            if (parts.length <= 1) {
                wrapper.and(w -> w.like(Product::getName, trimmed)
                        .or().like(Product::getDescription, trimmed)
                        .or().like(Product::getTags, trimmed));
            } else {
                for (String part : parts) {
                    String p = part.trim();
                    if (!StringUtils.hasText(p)) {
                        continue;
                    }
                    wrapper.and(inner -> inner
                            .like(Product::getName, p)
                            .or().like(Product::getDescription, p)
                            .or().like(Product::getTags, p));
                }
            }
        }
        if (minPrice != null) {
            wrapper.ge(Product::getPrice, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(Product::getPrice, maxPrice);
        }
        return wrapper;
    }

    private List<String> splitKeywordParts(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        String[] parts = keyword.trim().split("[\\s,，]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String normalized = part == null ? "" : part.trim();
            if (StringUtils.hasText(normalized) && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        if (result.isEmpty()) {
            return Collections.singletonList(keyword.trim());
        }
        return result;
    }

    private Map<String, Object> buildPriceBucket(String range, long count) {
        Map<String, Object> row = new HashMap<>();
        row.put("range", range);
        row.put("count", count);
        return row;
    }

    private void applyPublicSort(LambdaQueryWrapper<Product> wrapper, String keyword,
                                 String sortField, String sortOrder) {
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        String normalizedSortField = normalizeSortField(sortField);
        if ("id".equals(normalizedSortField)) {
            wrapper.orderBy(true, isAsc, Product::getId);
        } else if ("price".equals(normalizedSortField)) {
            wrapper.orderBy(true, isAsc, Product::getPrice);
        } else if ("sales".equals(normalizedSortField) || "salescount".equals(normalizedSortField)) {
            wrapper.orderBy(true, isAsc, Product::getSalesCount);
        } else if ("rating".equals(normalizedSortField)) {
            wrapper.orderBy(true, isAsc, Product::getRating);
        } else if ("createtime".equals(normalizedSortField)) {
            wrapper.orderBy(true, isAsc, Product::getCreateTime);
        } else if (StringUtils.hasText(keyword)) {
            wrapper.orderByDesc(Product::getSalesCount);
        } else {
            wrapper.orderByDesc(Product::getCreateTime);
        }
    }

    private void applyMerchantSort(LambdaQueryWrapper<Product> wrapper, String sortField, String sortOrder) {
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        String normalizedSortField = normalizeSortField(sortField);
        if (!StringUtils.hasText(normalizedSortField)) {
            wrapper.orderByDesc(Product::getId);
            return;
        }

        switch (normalizedSortField) {
            case "id":
                wrapper.orderBy(true, isAsc, Product::getId);
                break;
            case "price":
                wrapper.orderBy(true, isAsc, Product::getPrice);
                break;
            case "sales":
            case "salescount":
                wrapper.orderBy(true, isAsc, Product::getSalesCount);
                break;
            case "stock":
                wrapper.orderBy(true, isAsc, Product::getStock);
                break;
            case "name":
                wrapper.orderBy(true, isAsc, Product::getName);
                break;
            case "updatetime":
                wrapper.orderBy(true, isAsc, Product::getUpdateTime);
                break;
            case "createtime":
                wrapper.orderBy(true, isAsc, Product::getCreateTime);
                break;
            default:
                wrapper.orderByDesc(Product::getId);
                break;
        }
    }

    private String normalizeSortField(String sortField) {
        if (!StringUtils.hasText(sortField)) {
            return "";
        }
        return sortField.trim()
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
    }

    private List<Product> loadMerchantProductsOrThrow(Long merchantId, List<Long> productIds) {
        List<Long> distinctIds = productIds == null ? Collections.emptyList() : productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            throw new BusinessException("商品 ID 列表不能为空");
        }

        List<Product> products = this.list(new LambdaQueryWrapper<Product>()
                .eq(Product::getMerchantId, merchantId)
                .in(Product::getId, distinctIds));
        if (products.size() != distinctIds.size()) {
            throw new BusinessException("包含不存在的商品或无权操作的商品");
        }

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        return distinctIds.stream()
                .map(productMap::get)
                .collect(Collectors.toList());
    }

    private Product normalizeProductForPersistence(Product product) {
        if (product == null) {
            return null;
        }
        product.setName(product.getName());
        product.setDescription(product.getDescription());
        product.setImage(product.getImage());
        product.setImages(product.getImages());
        product.setTags(product.getTags());
        return product;
    }

    private Product refreshProductDetailCache(Long productId, String cacheKey) {
        Product product = this.getById(productId);
        ProductCacheEntry entry = new ProductCacheEntry();
        entry.product = product;
        if (product == null) {
            entry.expireAtMillis = System.currentTimeMillis() + withJitterMillis(60_000L, 0.2D);
            redisUtil.set(cacheKey, entry, withJitterSeconds(120, 0.2D), TimeUnit.SECONDS);
        } else {
            entry.expireAtMillis = System.currentTimeMillis() + withJitterMillis(300_000L, 0.2D);
            redisUtil.set(cacheKey, entry, withJitterSeconds(3600, 0.2D), TimeUnit.SECONDS);
        }
        return product;
    }

    private ProductCacheEntry readProductCacheEntry(String cacheKey) {
        Object raw = redisUtil.get(cacheKey);
        if (!(raw instanceof ProductCacheEntry)) {
            return null;
        }
        return (ProductCacheEntry) raw;
    }

    @SuppressWarnings("unchecked")
    private List<Product> readHotProductsCache(String cacheKey) {
        List<Product> cached = redisUtil.getList(cacheKey);
        if (cached == null || cached.isEmpty()) {
            return null;
        }
        for (Object item : cached) {
            if (!(item instanceof Product)) {
                return null;
            }
        }
        return cached;
    }

    private boolean tryLock(String lockKey, long seconds) {
        return Boolean.TRUE.equals(redisUtil.setIfAbsent(lockKey, "1", seconds, TimeUnit.SECONDS));
    }

    private void unlock(String lockKey) {
        redisUtil.delete(lockKey);
    }

    private String buildProductDetailCacheKey(Long productId) {
        return PRODUCT_DETAIL_CACHE_KEY_PREFIX + productId;
    }

    private String buildProductDetailLockKey(Long productId) {
        return PRODUCT_DETAIL_LOCK_KEY_PREFIX + productId;
    }

    @Override
    public void evictProductCaches(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        for (Long productId : productIds) {
            if (productId == null) {
                continue;
            }
            redisUtil.delete(buildProductDetailCacheKey(productId));
            redisStockService.evictStockCache(productId);
        }
        redisUtil.increment(PRODUCT_HOT_CACHE_VERSION_KEY);
        redisUtil.increment(PRODUCT_SEARCH_FACET_VERSION_KEY);
    }

    private long resolveHotCacheVersion() {
        Object raw = redisUtil.get(PRODUCT_HOT_CACHE_VERSION_KEY);
        long version = parseLong(raw);
        if (version > 0L) {
            return version;
        }
        redisUtil.set(PRODUCT_HOT_CACHE_VERSION_KEY, 1L, 7, TimeUnit.DAYS);
        return 1L;
    }

    private long resolveSearchFacetCacheVersion() {
        Object raw = redisUtil.get(PRODUCT_SEARCH_FACET_VERSION_KEY);
        long version = parseLong(raw);
        if (version > 0L) {
            return version;
        }
        redisUtil.set(PRODUCT_SEARCH_FACET_VERSION_KEY, 1L, 7, TimeUnit.DAYS);
        return 1L;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMapCache(String cacheKey) {
        Object raw = redisUtil.get(cacheKey);
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        return null;
    }

    private String buildSearchFacetCacheKey(long version, Long categoryId, String keyword,
                                            BigDecimal minPrice, BigDecimal maxPrice) {
        String raw = String.join("|",
                String.valueOf(categoryId == null ? 0L : categoryId),
                normalizeCachePart(keyword),
                minPrice == null ? "" : minPrice.stripTrailingZeros().toPlainString(),
                maxPrice == null ? "" : maxPrice.stripTrailingZeros().toPlainString());
        return PRODUCT_SEARCH_FACET_CACHE_KEY_PREFIX + version + ":" + Integer.toUnsignedString(raw.hashCode());
    }

    private String normalizeCachePart(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private long parseLong(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private long withJitterSeconds(long baseSeconds, double ratio) {
        long safeBase = Math.max(30L, baseSeconds);
        long maxJitter = (long) (safeBase * Math.max(0D, ratio));
        if (maxJitter <= 0L) {
            return safeBase;
        }
        return safeBase + ThreadLocalRandom.current().nextLong(maxJitter + 1L);
    }

    private long withJitterMillis(long baseMillis, double ratio) {
        long safeBase = Math.max(1_000L, baseMillis);
        long maxJitter = (long) (safeBase * Math.max(0D, ratio));
        if (maxJitter <= 0L) {
            return safeBase;
        }
        return safeBase + ThreadLocalRandom.current().nextLong(maxJitter + 1L);
    }

    private static class ProductCacheEntry {
        private Product product;
        private Long expireAtMillis;
    }

    private long getLongValue(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return new BigDecimal(value.toString());
    }
}
