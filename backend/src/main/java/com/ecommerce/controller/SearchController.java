package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.SearchHistory;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.SearchHistoryMapper;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.SearchQualityMetricsService;
import com.ecommerce.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final String HOT_SEARCH_CACHE_KEY = "search:hot:keywords";
    private static final int MAX_HISTORY_PER_USER = 30;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private SearchHistoryMapper searchHistoryMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SearchQualityMetricsService searchQualityMetricsService;

    /**
     * 记录搜索（用户搜索商品时调用）
     */
    @PostMapping("/record")
    public Result<?> recordSearch(@RequestBody Map<String, String> params,
                                   HttpServletRequest request) {
        moduleSwitchService.requireEnabled("search");
        if (!moduleSwitchService.isEnabled("search")) {
            return Result.success();
        }
        Long userId = (Long) request.getAttribute("userId");
        String keyword = params.get("keyword");
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BusinessException("搜索关键词不能为空");
        }
        keyword = keyword.trim();
        if (keyword.length() > 50) {
            keyword = keyword.substring(0, 50);
        }

        int updated = searchHistoryMapper.incrementCount(userId, keyword);
        if (updated == 0) {
            long count = searchHistoryMapper.selectCount(
                    new LambdaQueryWrapper<SearchHistory>()
                            .eq(SearchHistory::getUserId, userId));
            if (count >= MAX_HISTORY_PER_USER) {
                SearchHistory oldest = searchHistoryMapper.selectOne(
                        new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId)
                                .orderByAsc(SearchHistory::getUpdateTime)
                                .last("LIMIT 1"));
                if (oldest != null) {
                    searchHistoryMapper.deleteById(oldest.getId());
                }
            }

            SearchHistory history = new SearchHistory();
            history.setUserId(userId);
            history.setKeyword(keyword);
            history.setSearchCount(1);
            searchHistoryMapper.insert(history);
        }

        redisUtil.delete(HOT_SEARCH_CACHE_KEY);
        searchQualityMetricsService.recordQuery(false);

        return Result.success("搜索已记录");
    }

    /**
     * 获取用户搜索历史
     */
    @GetMapping("/history")
    public Result<?> getHistory(
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("search");
        if (!moduleSwitchService.isEnabled("search")) {
            return Result.success(Collections.emptyList());
        }
        Long userId = (Long) request.getAttribute("userId");
        List<SearchHistory> list = searchHistoryMapper.selectList(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
                        .orderByDesc(SearchHistory::getUpdateTime)
                        .last("LIMIT " + Math.min(limit, 50)));
        List<String> keywords = list.stream()
                .map(SearchHistory::getKeyword)
                .collect(Collectors.toList());
        return Result.success(keywords);
    }

    /**
     * 删除单条搜索历史
     */
    @DeleteMapping("/history/{id}")
    public Result<?> deleteHistory(@PathVariable Long id, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("search");
        if (!moduleSwitchService.isEnabled("search")) {
            return Result.success();
        }
        Long userId = (Long) request.getAttribute("userId");
        SearchHistory sh = searchHistoryMapper.selectById(id);
        if (sh == null || !sh.getUserId().equals(userId)) {
            throw new BusinessException("记录不存在");
        }
        searchHistoryMapper.deleteById(id);
        return Result.success("删除成功");
    }

    /**
     * 按关键词删除搜索历史
     */
    @DeleteMapping("/history/keyword")
    public Result<?> deleteByKeyword(@RequestParam String keyword, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("search");
        if (!moduleSwitchService.isEnabled("search")) {
            return Result.success();
        }
        Long userId = (Long) request.getAttribute("userId");
        searchHistoryMapper.delete(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
                        .eq(SearchHistory::getKeyword, keyword));
        return Result.success("删除成功");
    }

    /**
     * 清空搜索历史
     */
    @DeleteMapping("/history")
    public Result<?> clearHistory(HttpServletRequest request) {
        moduleSwitchService.requireEnabled("search");
        if (!moduleSwitchService.isEnabled("search")) {
            return Result.success();
        }
        Long userId = (Long) request.getAttribute("userId");
        searchHistoryMapper.delete(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId));
        return Result.success("搜索历史已清空");
    }

    /**
     * 热门搜索词（公开接口，无需登录）
     */
    @GetMapping("/hot")
    public Result<?> hotKeywords(@RequestParam(defaultValue = "10") int limit) {
        moduleSwitchService.requireEnabled("search");
        if (!moduleSwitchService.isEnabled("search")) {
            return Result.success(Collections.emptyList());
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cached = (List<Map<String, Object>>) redisUtil.get(HOT_SEARCH_CACHE_KEY);
        if (cached != null) {
            return Result.success(cached);
        }

        List<Map<String, Object>> hot = searchHistoryMapper.selectHotKeywords(Math.min(limit, 20));
        if (!hot.isEmpty()) {
            redisUtil.set(HOT_SEARCH_CACHE_KEY, hot, 1800, TimeUnit.SECONDS);
        }
        return Result.success(hot);
    }

    /**
     * 搜索建议/自动补全（公开接口，无需登录）
     */
    @GetMapping("/suggest")
    public Result<?> suggest(@RequestParam String keyword,
                              @RequestParam(defaultValue = "8") int limit) {
        moduleSwitchService.requireEnabled("search");
        if (!moduleSwitchService.isEnabled("search")) {
            return Result.success(Collections.emptyList());
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        String normalizedKeyword = keyword.trim();
        List<String> prefixSuggestions = searchHistoryMapper.selectSuggestions(normalizedKeyword, safeLimit);
        List<String> containsSuggestions = searchHistoryMapper.selectSuggestionsContaining(normalizedKeyword, safeLimit);
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (prefixSuggestions != null) {
            merged.addAll(prefixSuggestions);
        }
        if (containsSuggestions != null) {
            merged.addAll(containsSuggestions);
        }
        List<String> suggestions = merged.stream().limit(safeLimit).collect(Collectors.toList());
        return Result.success(suggestions);
    }

    /**
     * 搜索聚合查询（商品分页 + facets + 纠错词）
     */
    @GetMapping("/query")
    public Result<?> query(@RequestParam String keyword,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(required = false) BigDecimal minPrice,
                           @RequestParam(required = false) BigDecimal maxPrice,
                           @RequestParam(required = false) String sortBy,
                           @RequestParam(required = false) String sortOrder,
                           @RequestParam(required = false) Long categoryId) {
        moduleSwitchService.requireEnabled("search");
        if (!moduleSwitchService.isEnabled("search")) {
            return Result.success(Collections.emptyMap());
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        if (!StringUtils.hasText(normalizedKeyword)) {
            throw new BusinessException("搜索关键词不能为空");
        }

        String sortField = mapSortBy(sortBy);
        String normalizedOrder = normalizeSortOrder(sortOrder);
        IPage<Product> pageResult = productService.getProductPage(
                page, size, categoryId, normalizedKeyword, minPrice, maxPrice, sortField, normalizedOrder);

        String correctedKeyword = null;
        String usedKeyword = normalizedKeyword;
        if (pageResult.getTotal() == 0) {
            correctedKeyword = findCorrection(normalizedKeyword);
            if (StringUtils.hasText(correctedKeyword) && !Objects.equals(correctedKeyword, normalizedKeyword)) {
                IPage<Product> correctedPage = productService.getProductPage(
                        page, size, categoryId, correctedKeyword, minPrice, maxPrice, sortField, normalizedOrder);
                if (correctedPage.getTotal() > 0) {
                    pageResult = correctedPage;
                    usedKeyword = correctedKeyword;
                } else {
                    correctedKeyword = null;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalKeyword", normalizedKeyword);
        result.put("correctedKeyword", correctedKeyword);
        result.put("usedKeyword", usedKeyword);
        result.put("products", pageResult);
        result.put("facets", productService.getSearchFacets(categoryId, usedKeyword, minPrice, maxPrice));
        searchQualityMetricsService.recordQuery(StringUtils.hasText(correctedKeyword));
        return Result.success(result);
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 50) {
            normalized = normalized.substring(0, 50);
        }
        return normalized;
    }

    private String normalizeSortOrder(String sortOrder) {
        if (!StringUtils.hasText(sortOrder)) {
            return "desc";
        }
        return "asc".equalsIgnoreCase(sortOrder.trim()) ? "asc" : "desc";
    }

    private String mapSortBy(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return null;
        }
        String normalized = sortBy.trim();
        if ("综合".equals(normalized)) {
            return null;
        }
        if ("销量".equals(normalized)) {
            return "sales";
        }
        if ("价格".equals(normalized)) {
            return "price";
        }
        if ("最新".equals(normalized)) {
            return "createTime";
        }
        if ("好评".equals(normalized)) {
            return "rating";
        }
        return normalized;
    }

    private String findCorrection(String keyword) {
        List<String> allCandidates = collectCorrectionCandidates();
        if (allCandidates.isEmpty()) {
            return null;
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        int bestDistance = Integer.MAX_VALUE;
        String best = null;
        for (String candidate : allCandidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            String normalizedCandidate = candidate.trim();
            if (normalizedCandidate.equalsIgnoreCase(normalizedKeyword)) {
                return normalizedCandidate;
            }
            int distance = levenshtein(normalizedKeyword.toLowerCase(Locale.ROOT),
                    normalizedCandidate.toLowerCase(Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = normalizedCandidate;
            }
        }

        if (!StringUtils.hasText(best)) {
            return null;
        }
        int length = Math.max(1, normalizedKeyword.length());
        int threshold = length <= 4 ? 1 : Math.min(3, length / 3 + 1);
        return bestDistance <= threshold ? best : null;
    }

    private List<String> collectCorrectionCandidates() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        appendCandidateTokens(candidates, searchHistoryMapper.selectTopKeywordsForCorrection(120));
        appendCandidateTokens(candidates, productMapper.selectSearchCorrectionProductNames(120));
        appendCandidateTokens(candidates, productMapper.selectSearchCorrectionCategoryNames(80));
        appendCandidateTokens(candidates, productMapper.selectSearchCorrectionTagTerms(120));
        return candidates.stream().limit(800).collect(Collectors.toList());
    }

    private void appendCandidateTokens(Set<String> collector, List<String> values) {
        if (collector == null || values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalized = value.trim();
            if (!normalized.isEmpty()) {
                collector.add(normalized);
            }
            String[] parts = normalized.split("[\\s,，/|()（）·-]+");
            for (String part : parts) {
                if (!StringUtils.hasText(part)) {
                    continue;
                }
                String token = part.trim();
                if (token.length() >= 2 && token.length() <= 30) {
                    collector.add(token);
                }
            }
        }
    }

    private int levenshtein(String source, String target) {
        if (Objects.equals(source, target)) {
            return 0;
        }
        if (source == null || source.isEmpty()) {
            return target == null ? 0 : target.length();
        }
        if (target == null || target.isEmpty()) {
            return source.length();
        }

        int sourceLength = source.length();
        int targetLength = target.length();
        int[][] dp = new int[sourceLength + 1][targetLength + 1];
        for (int i = 0; i <= sourceLength; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= targetLength; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= sourceLength; i++) {
            for (int j = 1; j <= targetLength; j++) {
                int cost = source.charAt(i - 1) == target.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[sourceLength][targetLength];
    }
}
