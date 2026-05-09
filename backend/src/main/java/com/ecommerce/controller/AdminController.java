package com.ecommerce.controller;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Log;
import com.ecommerce.common.Result;
import com.ecommerce.config.StartupDependencyChecker;
import com.ecommerce.dto.AdminCreateMerchantDTO;
import com.ecommerce.entity.*;
import com.ecommerce.mapper.*;
import com.ecommerce.mq.MqEventPublisher;
import com.ecommerce.mq.RabbitMqNames;
import com.ecommerce.service.CouponAudienceService;
import com.ecommerce.service.DataAnalysisService;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ManagementWorkbenchBadgeService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.RolePermissionService;
import com.ecommerce.service.SearchQualityMetricsService;
import com.ecommerce.service.UserService;
import com.ecommerce.utils.BannerImageResolver;
import com.ecommerce.utils.RefundViewUtil;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.ecommerce.recommendation.ABTestFramework;
import com.ecommerce.recommendation.CollaborativeFiltering;
import com.ecommerce.recommendation.ContentBasedFiltering;
import com.ecommerce.recommendation.HybridRecommendationEngine;
import com.ecommerce.recommendation.UserPreferenceBootstrapService;
import com.ecommerce.service.RecommendationService;
import com.ecommerce.service.impl.KmeansCoverageBackfillService;
import org.springframework.transaction.annotation.Transactional;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "后台管理、运营分析、系统配置与推荐实验接口")
@SecurityRequirement(name = "BearerAuth")
public class AdminController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int SUPPORT_PENDING_TIMEOUT_MINUTES = 10;
    private static final int SUPPORT_PROCESSING_TIMEOUT_MINUTES = 30;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private RolePermissionService rolePermissionService;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private UserBehaviorMapper behaviorMapper;

    @Autowired
    private SearchHistoryMapper searchHistoryMapper;

    @Autowired
    private BannerMapper bannerMapper;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private CouponAudienceService couponAudienceService;

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private ProductReviewMapper reviewMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MqEventPublisher mqEventPublisher;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private HybridRecommendationEngine hybridEngine;

    @Autowired
    private CollaborativeFiltering collaborativeFiltering;

    @Autowired
    private ContentBasedFiltering contentBasedFiltering;

    @Autowired
    private ABTestFramework abTestFramework;

    @Autowired
    private DataAnalysisService dataAnalysisService;

    @Autowired
    private KmeansCoverageBackfillService kmeansCoverageBackfillService;

    @Autowired
    private ProfileChangeRequestMapper profileChangeRequestMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private AnalyticsBehaviorDailyMapper analyticsBehaviorDailyMapper;

    @Autowired
    private AnalyticsRecommendationExposureMapper analyticsRecommendationExposureMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ImConversationMapper imConversationMapper;

    @Autowired
    private ImTicketMapper imTicketMapper;

    @Autowired
    private ImMessageMapper imMessageMapper;

    @Autowired
    private SearchQualityMetricsService searchQualityMetricsService;

    @Autowired
    private StartupDependencyChecker startupDependencyChecker;

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @Autowired
    private ManagementWorkbenchBadgeService managementWorkbenchBadgeService;

    @Autowired
    private UserPreferenceBootstrapService userPreferenceBootstrapService;

    // ==================== 商家管理 ====================

    @GetMapping("/merchants")
    public Result<?> merchants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, "merchant");
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime);
        IPage<User> result = userService.page(new Page<>(page, size), wrapper);

        List<Map<String, Object>> records = new ArrayList<>();
        for (User merchant : result.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", merchant.getId());
            item.put("username", merchant.getUsername());
            item.put("nickname", merchant.getNickname());
            item.put("phone", merchant.getPhone());
            item.put("email", merchant.getEmail());
            item.put("avatar", merchant.getAvatar());
            item.put("status", merchant.getStatus());
            item.put("createTime", merchant.getCreateTime());

            LambdaQueryWrapper<Product> pWrapper = new LambdaQueryWrapper<>();
            pWrapper.eq(Product::getMerchantId, merchant.getId());
            List<Product> products = productService.list(pWrapper);
            item.put("productCount", products.size());
            long totalSales = products.stream().mapToLong(p -> p.getSalesCount() != null ? p.getSalesCount() : 0).sum();
            item.put("totalSales", totalSales);
            BigDecimal totalRevenue = products.stream()
                    .map(p -> p.getPrice().multiply(new BigDecimal(p.getSalesCount() != null ? p.getSalesCount() : 0)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.put("totalRevenue", totalRevenue);

            long orderCount = 0;
            if (!products.isEmpty()) {
                List<Long> pids = products.stream().map(Product::getId).collect(Collectors.toList());
                orderCount = orderService.count(new LambdaQueryWrapper<Order>()
                        .inSql(Order::getId, "SELECT DISTINCT order_id FROM order_item WHERE product_id IN ("
                                + pids.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")"));
            }
            item.put("orderCount", orderCount);
            records.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("pages", result.getPages());
        return Result.success(data);
    }

    @GetMapping("/merchants/stats")
    public Result<?> merchantStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long totalMerchants = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, "merchant"));
        long activeMerchants = userService.count(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "merchant").eq(User::getStatus, 1));
        stats.put("totalMerchants", totalMerchants);
        stats.put("activeMerchants", activeMerchants);

        List<User> allMerchants = userService.list(new LambdaQueryWrapper<User>().eq(User::getRole, "merchant"));
        long totalProducts = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (User m : allMerchants) {
            List<Product> products = productService.list(
                    new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, m.getId()));
            totalProducts += products.size();
            for (Product p : products) {
                int sales = p.getSalesCount() != null ? p.getSalesCount() : 0;
                totalRevenue = totalRevenue.add(p.getPrice().multiply(new BigDecimal(sales)));
            }
        }
        stats.put("totalProducts", totalProducts);
        stats.put("totalRevenue", totalRevenue);
        return Result.success(stats);
    }

    @Log(module = "商家管理", action = "创建商家")
    @PostMapping("/merchants")
    public Result<?> createMerchant(@Validated @RequestBody AdminCreateMerchantDTO dto) {
        User merchant = userService.createMerchant(
                dto.getPhone(), dto.getPassword(), dto.getNickname(), dto.getEmail());
        merchant.setPassword(null);
        return Result.success("商家创建成功", merchant);
    }

    @GetMapping("/dashboard")
    public Result<?> dashboard() {
        Map<String, Object> stats = orderService.getDashboardStats();

        // 钱包相关统计
        List<User> allUsers = userService.list(
                new LambdaQueryWrapper<User>().select(User::getBalance));
        BigDecimal totalBalance = allUsers.stream()
                .map(u -> u.getBalance() != null ? u.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalBalance", totalBalance);

        LambdaQueryWrapper<WalletTransaction> todayRechargeW = new LambdaQueryWrapper<>();
        todayRechargeW.eq(WalletTransaction::getType, "recharge");
        todayRechargeW.ge(WalletTransaction::getCreateTime, LocalDateTime.now().toLocalDate().atStartOfDay());
        List<WalletTransaction> todayList = walletTransactionMapper.selectList(todayRechargeW);
        BigDecimal todayRecharge = todayList.stream()
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("todayRecharge", todayRecharge);

        return Result.success(stats);
    }

    @GetMapping("/workbench/badge-counts")
    public Result<?> workbenchBadgeCounts(@RequestParam(required = false) String scope) {
        List<String> scopes = managementWorkbenchBadgeService.parseScopes(scope);
        return Result.success(managementWorkbenchBadgeService.getAdminBadgeCounts(scopes));
    }

    @GetMapping("/system/health")
    public Result<?> systemHealth(HttpServletRequest request) {
        requireAdminRequest(request);

        Map<String, Object> data = new LinkedHashMap<>(startupDependencyChecker.getSnapshot());
        Map<String, Object> switches = moduleSwitchService.getAllSwitches();

        long total = switches.size();
        long enabled = 0;
        for (Object value : switches.values()) {
            if (!(value instanceof Map)) {
                continue;
            }
            Object enabledObj = ((Map<?, ?>) value).get("enabled");
            if (Boolean.TRUE.equals(enabledObj)) {
                enabled++;
            }
        }

        long disabled = Math.max(0, total - enabled);
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put("status", disabled == 0 ? "UP" : (enabled > 0 ? "WARN" : "DOWN"));
        modules.put("target", "功能开关");
        modules.put("message", disabled == 0
                ? "全部模块运行中"
                : String.format("已启用 %d 个，已关闭 %d 个", enabled, disabled));
        modules.put("total", total);
        modules.put("enabled", enabled);
        modules.put("disabled", disabled);
        data.put("modules", modules);

        return Result.success(data);
    }

    @GetMapping("/users")
    public Result<?> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {
        IPage<User> result = userService.getUserPage(page, size, keyword, role);
        return Result.success(result);
    }

    @Log(module = "用户管理", action = "修改用户状态")
    @PutMapping("/users/{id}/status")
    public Result<?> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> params) {
        Integer status = params.get("status");
        if (status == null) {
            throw new BusinessException("请指定用户状态");
        }
        ensureNonAdminUser(id);
        userService.updateStatus(id, status);
        return Result.success("用户状态更新成功");
    }

    @Log(module = "用户管理", action = "删除用户")
    @DeleteMapping("/users/{id}")
    public Result<?> deleteUser(@PathVariable Long id) {
        ensureNonAdminUser(id);
        userService.removeById(id);
        return Result.success("用户删除成功");
    }

    @GetMapping("/products")
    public Result<?> products(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(Product::getName, keyword)
                    .or().like(Product::getDescription, keyword));
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        wrapper.orderByDesc(Product::getId);
        IPage<Product> result = productService.page(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @Log(module = "商品管理", action = "修改商品状态")
    @PutMapping("/products/{id}/status")
    public Result<?> updateProductStatus(@PathVariable Long id, @RequestBody Map<String, Integer> params) {
        Integer status = params.get("status");
        if (status == null) {
            throw new BusinessException("请指定商品状态");
        }
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        productService.updateById(product);
        return Result.success("商品状态更新成功");
    }

    @Log(module = "商品管理", action = "删除商品")
    @DeleteMapping("/products/{id}")
    public Result<?> deleteProduct(@PathVariable Long id) {
        productService.removeById(id);
        return Result.success("商品删除成功");
    }

    @GetMapping("/orders")
    public Result<?> orders(
            @RequestParam(defaultValue = "-1") Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = page <= 0 ? 1 : page;
        int safeSize = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        return Result.success(orderService.getAllOrders(status == -1 ? null : status, safePage, safeSize));
    }

    @Log(module = "订单管理", action = "修改订单状态")
    @PutMapping("/orders/{id}/status")
    public Result<?> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, Integer> params) {
        Integer status = params.get("status");
        if (status == null) {
            throw new BusinessException("请指定订单状态");
        }
        orderService.updateOrderStatus(id, status);
        return Result.success("订单状态更新成功");
    }

    @Log(module = "分类管理", action = "创建分类")
    @PostMapping("/categories")
    public Result<?> createCategory(@RequestBody Category category) {
        categoryMapper.insert(category);
        return Result.success("分类创建成功", category);
    }

    @Log(module = "分类管理", action = "更新分类")
    @PutMapping("/categories/{id}")
    public Result<?> updateCategory(@PathVariable Long id, @RequestBody Category category) {
        category.setId(id);
        categoryMapper.updateById(category);
        return Result.success("分类更新成功");
    }

    @Log(module = "分类管理", action = "删除分类")
    @DeleteMapping("/categories/{id}")
    public Result<?> deleteCategory(@PathVariable Long id) {
        categoryMapper.deleteById(id);
        return Result.success("分类删除成功");
    }

    @GetMapping("/analytics/behavior")
    public Result<?> behaviorAnalytics() {
        List<Map<String, Object>> snapshotStats = loadBehaviorAnalyticsSnapshot();
        if (snapshotStats != null) {
            return Result.success(snapshotStats);
        }
        List<Map<String, Object>> stats = behaviorMapper.selectBehaviorStats();
        return Result.success(stats);
    }

    @GetMapping("/analytics/search-behavior")
    public Result<?> searchBehaviorAnalytics(@RequestParam(defaultValue = "14") int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDateTime startTime = LocalDateTime.now().minusDays(safeDays);

        Map<String, Object> qualityRaw = searchQualityMetricsService.getSummary();
        List<Map<String, Object>> trendRaw = searchQualityMetricsService.getRecentDailyMetrics(safeDays);
        List<Map<String, Object>> hotKeywordsRaw = searchHistoryMapper.selectHotKeywords(10);
        Map<String, Object> conversionRaw = behaviorMapper.selectSearchConversionStats(startTime);

        Map<String, Object> quality = new LinkedHashMap<>();
        long totalQueries = readLong(qualityRaw == null ? null : qualityRaw.get("totalQueries"));
        long correctedHits = readLong(qualityRaw == null ? null : qualityRaw.get("correctedHits"));
        quality.put("totalQueries", totalQueries);
        quality.put("correctedHits", correctedHits);
        quality.put("correctionHitRate", percent(correctedHits, totalQueries));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> row : trendRaw == null ? Collections.<Map<String, Object>>emptyList() : trendRaw) {
            Map<String, Object> item = new LinkedHashMap<>();
            long dailyTotal = readLong(row == null ? null : row.get("totalQueries"));
            long dailyCorrected = readLong(row == null ? null : row.get("correctedHits"));
            Object rawDate = row == null ? null : row.get("date");
            item.put("date", rawDate == null ? "" : String.valueOf(rawDate));
            item.put("totalQueries", dailyTotal);
            item.put("correctedHits", dailyCorrected);
            item.put("correctionHitRate", percent(dailyCorrected, dailyTotal));
            trend.add(item);
        }

        List<Map<String, Object>> topKeywords = new ArrayList<>();
        for (Map<String, Object> row : hotKeywordsRaw == null ? Collections.<Map<String, Object>>emptyList() : hotKeywordsRaw) {
            Map<String, Object> item = new LinkedHashMap<>();
            Object rawKeyword = row == null ? null : row.get("keyword");
            item.put("keyword", rawKeyword == null ? "" : String.valueOf(rawKeyword));
            item.put("totalCount", readLong(row == null ? null : row.get("total_count")));
            topKeywords.add(item);
        }

        long searchUserCount = readLong(conversionRaw == null ? null : conversionRaw.get("search_user_count"));
        long searchToClickUserCount = readLong(conversionRaw == null ? null : conversionRaw.get("search_to_click_user_count"));
        long searchToPurchaseUserCount = readLong(conversionRaw == null ? null : conversionRaw.get("search_to_purchase_user_count"));
        Map<String, Object> conversion = new LinkedHashMap<>();
        conversion.put("searchUserCount", searchUserCount);
        conversion.put("searchToClickUserCount", searchToClickUserCount);
        conversion.put("searchToPurchaseUserCount", searchToPurchaseUserCount);
        conversion.put("searchToClickRate", percent(searchToClickUserCount, searchUserCount));
        conversion.put("searchToPurchaseRate", percent(searchToPurchaseUserCount, searchUserCount));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", safeDays);
        data.put("quality", quality);
        data.put("trend", trend);
        data.put("topKeywords", topKeywords);
        data.put("conversion", conversion);
        return Result.success(data);
    }

    @GetMapping("/analytics/sales")
    public Result<?> salesAnalytics() {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> dashStats = orderService.getDashboardStats();
        data.put("totalRevenue", dashStats.get("totalRevenue"));
        data.put("recentStats", dashStats.get("recentStats"));

        List<Product> hotProducts = productService.getHotProducts(10);
        data.put("hotProducts", hotProducts);

        List<Map<String, Object>> categoryStats = productService.getCategorySalesStats();
        data.put("categorySales", categoryStats);

        return Result.success(data);
    }

    @GetMapping("/analytics/recommendation")
    public Result<?> recommendationAnalytics(@RequestParam(defaultValue = "30") int days) {
        requireModuleEnabled("recommendation");
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> behaviorStats = behaviorMapper.selectBehaviorStats();
        data.put("behaviorStats", behaviorStats);
        data.put("totalUsers", userService.count());
        data.put("totalProducts", productService.count());
        data.put("performance", recommendationService.getRecommendationMetrics(days));
        return Result.success(data);
    }

    @GetMapping("/analytics/operations")
    public Result<?> operationsAnalytics(@RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDateTime startTime = LocalDateTime.now().minusDays(safeDays);

        Map<String, Object> searchSummary = new LinkedHashMap<>(searchQualityMetricsService.getSummary());
        searchSummary.put("daily", searchQualityMetricsService.getRecentDailyMetrics(Math.min(safeDays, 14)));

        long totalReviews = reviewMapper.selectCount(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getStatus, 1));
        long helpfulReviews = reviewMapper.countHelpfulReviews();
        long helpfulVotes = reviewMapper.sumHelpfulCount();
        long lowRatingReviews = reviewMapper.selectCount(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getStatus, 1)
                .le(ProductReview::getRating, 2));
        Map<String, Object> reviewSummary = new LinkedHashMap<>();
        reviewSummary.put("totalReviews", totalReviews);
        reviewSummary.put("helpfulReviews", helpfulReviews);
        reviewSummary.put("helpfulVotes", helpfulVotes);
        reviewSummary.put("helpfulRate", percent(helpfulReviews, totalReviews));
        reviewSummary.put("lowRatingReviews", lowRatingReviews);

        Map<String, Object> recommendationRaw = analyticsRecommendationExposureMapper.selectBusinessMetrics(startTime);
        long recommendationExposureCount = readLong(recommendationRaw == null ? null : recommendationRaw.get("exposureCount"));
        long recommendationClickCount = readLong(recommendationRaw == null ? null : recommendationRaw.get("clickCount"));
        long recommendationPurchaseCount = readLong(recommendationRaw == null ? null : recommendationRaw.get("purchaseCount"));
        BigDecimal recommendationAov = readBigDecimal(recommendationRaw == null ? null : recommendationRaw.get("aov"));
        Map<String, Object> recommendationSummary = new LinkedHashMap<>();
        recommendationSummary.put("exposureCount", recommendationExposureCount);
        recommendationSummary.put("clickCount", recommendationClickCount);
        recommendationSummary.put("purchaseCount", recommendationPurchaseCount);
        recommendationSummary.put("ctr", percent(recommendationClickCount, recommendationExposureCount));
        recommendationSummary.put("orderRate", percent(recommendationPurchaseCount, recommendationExposureCount));
        recommendationSummary.put("aov", recommendationAov.setScale(2, RoundingMode.HALF_UP));
        recommendationSummary.put("daily", normalizeRecommendationDaily(
                analyticsRecommendationExposureMapper.selectDailyMetrics(startTime)));

        Map<String, Object> supportSummary = buildSupportSummary(startTime);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", safeDays);
        data.put("search", searchSummary);
        data.put("review", reviewSummary);
        data.put("recommendation", recommendationSummary);
        data.put("support", supportSummary);
        return Result.success(data);
    }

    // ==================== 大数据分析 ====================

    @GetMapping("/analysis/funnel")
    public Result<?> funnelAnalysis() {
        return Result.success(dataAnalysisService.funnelAnalysis());
    }

    @GetMapping("/analysis/rfm")
    public Result<?> rfmAnalysis() {
        return Result.success(dataAnalysisService.rfmSegmentation());
    }

    @GetMapping("/analysis/association")
    public Result<?> associationRules(
            @RequestParam(defaultValue = "1") int minSupport,
            @RequestParam(defaultValue = "0.1") double minConfidence) {
        return Result.success(dataAnalysisService.associationRules(minSupport, minConfidence));
    }

    @GetMapping("/analysis/retention")
    public Result<?> retentionAnalysis() {
        return Result.success(dataAnalysisService.retentionAnalysis());
    }

    @GetMapping("/analysis/sales-trend")
    public Result<?> salesTrendAnalysis() {
        return Result.success(dataAnalysisService.salesTrendAnalysis());
    }

    @GetMapping("/analysis/heatmap")
    public Result<?> activityHeatmap() {
        return Result.success(dataAnalysisService.activityHeatmap());
    }

    @GetMapping("/analysis/summary")
    public Result<?> analysisSummary() {
        return Result.success(dataAnalysisService.analysisSummary());
    }

    @GetMapping("/analysis/health")
    public Result<?> analysisHealth() {
        return Result.success(dataAnalysisService.analysisHealthScore());
    }

    @GetMapping("/analysis/core-kpis")
    public Result<?> coreKpiDashboard(@RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDateTime startTime = LocalDate.now().minusDays(Math.max(0, safeDays - 1)).atStartOfDay();

        long dau = readLong(firstMapValue(behaviorMapper.selectMaps(
                new QueryWrapper<UserBehavior>()
                        .select("COUNT(DISTINCT user_id) AS cnt")
                        .ge("create_time", startTime)),
                "cnt"));

        Map<String, Object> recommendationRaw = analyticsRecommendationExposureMapper.selectBusinessMetrics(startTime);
        long exposureCount = readLong(recommendationRaw == null ? null : recommendationRaw.get("exposureCount"));
        long clickCount = readLong(recommendationRaw == null ? null : recommendationRaw.get("clickCount"));
        long purchaseCount = readLong(recommendationRaw == null ? null : recommendationRaw.get("purchaseCount"));

        List<Order> paidOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .in(Order::getStatus,
                                Constants.OrderStatus.PAID,
                                Constants.OrderStatus.SHIPPED,
                                Constants.OrderStatus.COMPLETED,
                                Constants.OrderStatus.REFUNDED)
                        .ge(Order::getCreateTime, startTime));
        BigDecimal gmv = BigDecimal.ZERO;
        Map<Long, Integer> orderCountByUser = new HashMap<>();
        for (Order order : paidOrders) {
            if (order == null || order.getTotalAmount() == null) {
                continue;
            }
            gmv = gmv.add(order.getTotalAmount());
            if (order.getUserId() != null) {
                orderCountByUser.merge(order.getUserId(), 1, Integer::sum);
            }
        }
        long paidOrderCount = paidOrders.size();
        long purchasingUserCount = orderCountByUser.size();
        long repurchaseUserCount = orderCountByUser.values().stream().filter(cnt -> cnt != null && cnt >= 2).count();

        BigDecimal aov = paidOrderCount <= 0
                ? BigDecimal.ZERO
                : gmv.divide(BigDecimal.valueOf(paidOrderCount), 2, RoundingMode.HALF_UP);

        long refundedOrderCount = refundMapper.selectCount(
                new LambdaQueryWrapper<RefundRequest>()
                        .eq(RefundRequest::getStatus, Constants.RefundStatus.REFUNDED)
                        .ge(RefundRequest::getUpdateTime, startTime));

        LocalDateTime cohortStart = LocalDate.now().minusDays(14).atStartOfDay();
        LocalDateTime cohortEnd = LocalDate.now().minusDays(7).plusDays(1).atStartOfDay();
        List<User> cohortUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .eq(User::getRole, Constants.Role.USER)
                        .ge(User::getCreateTime, cohortStart)
                        .lt(User::getCreateTime, cohortEnd));
        List<Long> cohortUserIds = cohortUsers.stream()
                .map(User::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        long retainedUserCount = countRetainedUsers(cohortUserIds, LocalDate.now().minusDays(7).atStartOfDay());

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("dau", dau);
        kpis.put("ctr", percent(clickCount, exposureCount));
        kpis.put("cvr", percent(purchaseCount, exposureCount));
        kpis.put("gmv", gmv.setScale(2, RoundingMode.HALF_UP));
        kpis.put("aov", aov);
        kpis.put("repurchaseRate", percent(repurchaseUserCount, purchasingUserCount));
        kpis.put("retention7d", percent(retainedUserCount, cohortUserIds.size()));
        kpis.put("refundRate", percent(refundedOrderCount, paidOrderCount));

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("days", safeDays);
        context.put("startTime", startTime);
        context.put("endTime", LocalDateTime.now());
        context.put("exposureCount", exposureCount);
        context.put("clickCount", clickCount);
        context.put("purchaseCount", purchaseCount);
        context.put("paidOrderCount", paidOrderCount);
        context.put("purchasingUserCount", purchasingUserCount);
        context.put("repurchaseUserCount", repurchaseUserCount);
        context.put("cohortUserCount", cohortUserIds.size());
        context.put("retainedUserCount", retainedUserCount);
        context.put("refundedOrderCount", refundedOrderCount);

        Map<String, Object> formula = new LinkedHashMap<>();
        formula.put("ctr", "clickCount / exposureCount * 100");
        formula.put("cvr", "purchaseCount / exposureCount * 100");
        formula.put("aov", "gmv / paidOrderCount");
        formula.put("repurchaseRate", "repurchaseUserCount / purchasingUserCount * 100");
        formula.put("retention7d", "retainedUserCount / cohortUserCount * 100");
        formula.put("refundRate", "refundedOrderCount / paidOrderCount * 100");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpis", kpis);
        result.put("context", context);
        result.put("formula", formula);
        return Result.success(result);
    }

    @GetMapping("/banners")
    public Result<?> banners() {
        List<Banner> list = bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>().orderByAsc(Banner::getSortOrder));
        BannerImageResolver.normalize(list);
        return Result.success(list);
    }

    @Log(module = "Banner管理", action = "创建Banner")
    @PostMapping("/banners")
    public Result<?> createBanner(@RequestBody Banner banner) {
        bannerMapper.insert(banner);
        return Result.success("Banner创建成功", banner);
    }

    @Log(module = "Banner管理", action = "更新Banner")
    @PutMapping("/banners/{id}")
    public Result<?> updateBanner(@PathVariable Long id, @RequestBody Banner banner) {
        banner.setId(id);
        bannerMapper.updateById(banner);
        return Result.success("Banner更新成功");
    }

    @Log(module = "Banner管理", action = "删除Banner")
    @DeleteMapping("/banners/{id}")
    public Result<?> deleteBanner(@PathVariable Long id) {
        bannerMapper.deleteById(id);
        return Result.success("Banner删除成功");
    }

    // ==================== 优惠券管理 ====================

    @GetMapping("/coupons")
    public Result<?> coupons(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        requireModuleEnabled("coupon");
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Coupon::getStatus, status);
        }
        wrapper.orderByDesc(Coupon::getCreateTime);
        return Result.success(couponMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Log(module = "优惠券管理", action = "创建优惠券")
    @PostMapping("/coupons")
    public Result<?> createCoupon(@RequestBody Coupon coupon) {
        requireModuleEnabled("coupon");
        Coupon preparedCoupon = prepareCouponForSave(coupon, null);
        validateCouponPayload(preparedCoupon);
        validateDiscountCouponValue(preparedCoupon);
        couponMapper.insert(preparedCoupon);
        return Result.success("优惠券创建成功", coupon);
    }

    @Log(module = "优惠券管理", action = "更新优惠券")
    @PutMapping("/coupons/{id}")
    public Result<?> updateCoupon(@PathVariable Long id, @RequestBody Coupon coupon) {
        requireModuleEnabled("coupon");
        Coupon existing = couponMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("优惠券不存在");
        }
        Coupon preparedCoupon = prepareCouponForSave(coupon, existing);
        preparedCoupon.setId(id);
        validateCouponPayload(preparedCoupon);
        validateDiscountCouponValue(preparedCoupon);
        couponMapper.updateById(preparedCoupon);
        return Result.success("优惠券更新成功");
    }

    @Log(module = "优惠券管理", action = "删除优惠券")
    @DeleteMapping("/coupons/{id}")
    public Result<?> deleteCoupon(@PathVariable Long id) {
        requireModuleEnabled("coupon");
        couponMapper.deleteById(id);
        return Result.success("优惠券删除成功");
    }

    // ==================== 退款管理 ====================

    @GetMapping("/refunds")
    public Result<?> refunds(
            @RequestParam(defaultValue = "-1") Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireModuleEnabled("refund");
        LambdaQueryWrapper<RefundRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null && status >= 0) {
            wrapper.eq(RefundRequest::getStatus, status);
        }
        wrapper.orderByDesc(RefundRequest::getCreateTime);
        IPage<RefundRequest> refundPage = refundMapper.selectPage(new Page<>(page, size), wrapper);
        refundPage.getRecords().forEach(refund -> {
            Order order = orderService.getById(refund.getOrderId());
            if (order != null) {
                refund.setOrderNo(order.getOrderNo());
            }
            User user = userService.getById(refund.getUserId());
            if (user != null) {
                refund.setUsername(user.getUsername());
            }
            RefundViewUtil.enrichRefundView(refund);
        });
        return Result.success(refundPage);
    }

    // ==================== 评价管理 ====================

    @GetMapping("/reviews")
    public Result<?> reviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        requireModuleEnabled("review");
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ProductReview::getStatus, status);
        }
        wrapper.orderByDesc(ProductReview::getCreateTime);
        IPage<ProductReview> reviewPage = reviewMapper.selectPage(new Page<>(page, size), wrapper);
        reviewPage.getRecords().forEach(review -> {
            User user = userService.getById(review.getUserId());
            if (user != null) {
                String displayName = (user.getNickname() != null && !user.getNickname().isEmpty())
                        ? user.getNickname() : user.getUsername();
                review.setUsername(displayName);
                review.setAvatar(user.getAvatar());
            }
            Product product = productService.getById(review.getProductId());
            if (product != null) {
                review.setProductName(product.getName());
            }
        });
        return Result.success(reviewPage);
    }

    @Log(module = "评论管理", action = "修改评论状态")
    @PutMapping("/reviews/{id}/status")
    public Result<?> updateReviewStatus(@PathVariable Long id, @RequestBody Map<String, Integer> params) {
        requireModuleEnabled("review");
        Integer st = params.get("status");
        if (st == null) throw new BusinessException("请指定状态");
        ProductReview r = new ProductReview();
        r.setId(id);
        r.setStatus(st);
        reviewMapper.updateById(r);
        return Result.success("评价状态更新成功");
    }

    @Log(module = "评论管理", action = "删除评论")
    @DeleteMapping("/reviews/{id}")
    public Result<?> deleteReview(@PathVariable Long id) {
        requireModuleEnabled("review");
        reviewMapper.deleteById(id);
        return Result.success("评价删除成功");
    }

    // ==================== 系统通知 ====================

    @Log(module = "系统通知", action = "群发公告")
    @PostMapping("/messages/broadcast")
    public Result<?> broadcastMessage(@RequestBody Message message) {
        requireModuleEnabled("message");
        if (mqEventPublisher.isEnabled()) {
            JSONObject payload = new JSONObject();
            payload.put("title", message.getTitle());
            payload.put("content", message.getContent());
            payload.put("messageType",
                    message.getType() == null || message.getType().trim().isEmpty()
                            ? Constants.MessageType.SYSTEM
                            : message.getType());
            mqEventPublisher.publishEvent(RabbitMqNames.ROUTING_MESSAGE_BROADCAST, "broadcast", payload);
            return Result.success("公告已进入异步发送队列");
        }

        List<User> users = userService.list(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        int count = 0;
        for (User user : users) {
            Message msg = new Message();
            msg.setUserId(user.getId());
            msg.setTitle(message.getTitle());
            msg.setContent(message.getContent());
            msg.setType(message.getType() == null || message.getType().trim().isEmpty()
                    ? "system"
                    : message.getType());
            msg.setIsRead(0);
            messageMapper.insert(msg);
            managementWorkbenchRealtimeService.notifyUserMessageChanged(
                    user.getId(),
                    "user-message-created"
            );
            managementWorkbenchRealtimeService.notifyMerchantMessageChanged(
                    user.getId(),
                    "merchant-message-created",
                    Collections.singletonMap("scope", "message")
            );
            count++;
        }
        return Result.success("已发送给 " + count + " 个用户");
    }

    // ==================== 操作日志 ====================

    @Autowired
    private OperationLogMapper operationLogMapper;

    @GetMapping("/logs")
    public Result<?> operationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (module != null && !module.isEmpty()) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(OperationLog::getUsername, keyword)
                    .or().like(OperationLog::getAction, keyword)
                    .or().like(OperationLog::getModule, keyword));
        }
        if (startTime != null) {
            wrapper.ge(OperationLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(OperationLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return Result.success(operationLogMapper.selectPage(new Page<>(page, size), wrapper));
    }

    // ==================== 钱包管理 ====================

    @GetMapping("/wallet/stats")
    public Result<?> walletStats() {
        requireModuleEnabled("wallet");
        Map<String, Object> stats = new LinkedHashMap<>();

        // 用户总余额
        List<User> allUsers = userService.list(
                new LambdaQueryWrapper<User>().select(User::getBalance));
        BigDecimal totalBalance = allUsers.stream()
                .map(u -> u.getBalance() != null ? u.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalBalance", totalBalance);

        // 总充值金额
        LambdaQueryWrapper<WalletTransaction> rechargeW = new LambdaQueryWrapper<>();
        rechargeW.eq(WalletTransaction::getType, "recharge");
        List<WalletTransaction> rechargeTxs = walletTransactionMapper.selectList(rechargeW);
        BigDecimal totalRecharge = rechargeTxs.stream()
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalRecharge", totalRecharge);
        stats.put("rechargeCount", rechargeTxs.size());

        // 总消费金额 (pay类型，amount为负)
        LambdaQueryWrapper<WalletTransaction> payW = new LambdaQueryWrapper<>();
        payW.eq(WalletTransaction::getType, "pay");
        List<WalletTransaction> payTxs = walletTransactionMapper.selectList(payW);
        BigDecimal totalSpent = payTxs.stream()
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalSpent", totalSpent);
        stats.put("payCount", payTxs.size());

        // 总退款金额
        LambdaQueryWrapper<WalletTransaction> refundW = new LambdaQueryWrapper<>();
        refundW.eq(WalletTransaction::getType, "refund");
        List<WalletTransaction> refundTxs = walletTransactionMapper.selectList(refundW);
        BigDecimal totalRefund = refundTxs.stream()
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalRefund", totalRefund);
        stats.put("refundCount", refundTxs.size());

        // 今日充值
        LambdaQueryWrapper<WalletTransaction> todayW = new LambdaQueryWrapper<>();
        todayW.eq(WalletTransaction::getType, "recharge");
        todayW.ge(WalletTransaction::getCreateTime, LocalDateTime.now().toLocalDate().atStartOfDay());
        List<WalletTransaction> todayTxs = walletTransactionMapper.selectList(todayW);
        BigDecimal todayRecharge = todayTxs.stream()
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("todayRecharge", todayRecharge);
        stats.put("todayRechargeCount", todayTxs.size());

        return Result.success(stats);
    }

    @GetMapping("/wallet/transactions")
    public Result<?> walletTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword) {
        requireModuleEnabled("wallet");
        LambdaQueryWrapper<WalletTransaction> wrapper = new LambdaQueryWrapper<>();
        if (type != null && !type.isEmpty()) {
            wrapper.eq(WalletTransaction::getType, type);
        }
        if (userId != null) {
            wrapper.eq(WalletTransaction::getUserId, userId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(WalletTransaction::getDescription, keyword);
        }
        wrapper.orderByDesc(WalletTransaction::getCreateTime);
        IPage<WalletTransaction> result = walletTransactionMapper.selectPage(new Page<>(page, size), wrapper);

        // 附带用户信息
        List<Map<String, Object>> records = new ArrayList<>();
        for (WalletTransaction tx : result.getRecords()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", tx.getId());
            record.put("userId", tx.getUserId());
            record.put("type", tx.getType());
            record.put("amount", tx.getAmount());
            record.put("balanceBefore", tx.getBalanceBefore());
            record.put("balanceAfter", tx.getBalanceAfter());
            record.put("orderNo", tx.getOrderNo());
            record.put("description", tx.getDescription());
            record.put("createTime", tx.getCreateTime());
            User user = userService.getById(tx.getUserId());
            if (user != null) {
                record.put("username", user.getNickname() != null && !user.getNickname().isEmpty()
                        ? user.getNickname() : user.getUsername());
                record.put("phone", user.getPhone());
            }
            records.add(record);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("pages", result.getPages());
        return Result.success(data);
    }

    @GetMapping("/wallet/user-balances")
    public Result<?> userBalances(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String keyword) {
        requireModuleEnabled("wallet");
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, "user");
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword));
        }
        wrapper.orderByDesc(User::getBalance);
        IPage<User> result = userService.page(new Page<>(page, size), wrapper);

        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : result.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("nickname", u.getNickname());
            item.put("phone", u.getPhone());
            item.put("avatar", u.getAvatar());
            item.put("balance", u.getBalance() != null ? u.getBalance() : BigDecimal.ZERO);
            item.put("status", u.getStatus());
            item.put("createTime", u.getCreateTime());
            list.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", list);
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("pages", result.getPages());
        return Result.success(data);
    }

    @Log(module = "钱包管理", action = "管理员调整余额")
    @PostMapping("/wallet/adjust")
    @Transactional
    public Result<?> adjustBalance(@RequestBody Map<String, Object> params) {
        requireModuleEnabled("wallet");
        Long targetUserId = Long.parseLong(params.get("userId").toString());
        BigDecimal amount = new BigDecimal(params.get("amount").toString());
        String reason = params.get("reason") != null ? params.get("reason").toString() : "管理员手动调整";

        User user = userService.getById(targetUserId);
        if (user == null) throw new BusinessException("用户不存在");

        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            userMapper.addBalance(targetUserId, amount);
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            int affected = userMapper.deductBalance(targetUserId, amount.abs());
            if (affected == 0) {
                throw new BusinessException("用户余额不足，当前余额 ¥" + user.getBalance());
            }
        } else {
            throw new BusinessException("调整金额不能为0");
        }

        User afterUser = userService.getById(targetUserId);
        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(targetUserId);
        tx.setType(amount.compareTo(BigDecimal.ZERO) > 0 ? "recharge" : "pay");
        tx.setAmount(amount);
        tx.setBalanceBefore(amount.compareTo(BigDecimal.ZERO) > 0
                ? afterUser.getBalance().subtract(amount)
                : afterUser.getBalance().add(amount.abs()));
        tx.setBalanceAfter(afterUser.getBalance());
        tx.setDescription("[管理员] " + reason);
        walletTransactionMapper.insert(tx);

        return Result.success("余额调整成功，当前余额: ¥" + afterUser.getBalance());
    }

    // ==================== 推荐算法管理 ====================

    @GetMapping("/recommend/preview/{userId}")
    public Result<?> recommendPreview(@PathVariable Long userId,
                                       @RequestParam(defaultValue = "10") int limit) {
        requireModuleEnabled("recommendation");
        userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        ensureSegmentCoverageForUser(userId);
        Map<String, Object> result = recommendationService.getPersonalRecommendationsWithExplanation(
                userId, Math.min(limit, 50), true);
        return Result.success(result);
    }

    @GetMapping("/recommend/realtime/{userId}")
    public Result<?> recommendRealtime(@PathVariable Long userId,
                                       @RequestParam(defaultValue = "10") int limit) {
        requireModuleEnabled("recommendation");
        userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        ensureSegmentCoverageForUser(userId);
        Map<String, Object> result = recommendationService.getRealtimeRecommendationDashboard(
                userId, Math.min(limit, 50), true);
        return Result.success(result);
    }

    @GetMapping("/recommend/compare/{userId}")
    public Result<?> recommendCompare(@PathVariable Long userId,
                                       @RequestParam(defaultValue = "10") int limit) {
        requireModuleEnabled("recommendation");
        userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        ensureSegmentCoverageForUser(userId);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        Map<String, Object> data = new LinkedHashMap<>();

        List<Long> cfIds = collaborativeFiltering.userBasedRecommend(userId, safeLimit);
        List<Product> cfProducts = cfIds.isEmpty() ? Collections.emptyList() : recommendationService.getProductsByIds(cfIds);

        List<Long> cbIds = contentBasedFiltering.recommend(userId, safeLimit);
        List<Product> cbProducts = cbIds.isEmpty() ? Collections.emptyList() : recommendationService.getProductsByIds(cbIds);

        HybridRecommendationEngine.RecommendationDecision hybridDecision = hybridEngine.recommendDetailedForGroup(
                userId,
                safeLimit,
                ABTestFramework.ExperimentGroup.HYBRID,
                false
        );
        List<Long> hybridIds = hybridDecision.getProductIds();
        List<Product> hybridProducts = hybridIds.isEmpty() ? Collections.emptyList() : recommendationService.getProductsByIds(hybridIds);

        List<Product> hotProducts = recommendationService.getHotRecommendations(safeLimit);
        Map<String, Object> onlinePayload = recommendationService.getPersonalRecommendationsWithExplanation(userId, safeLimit, true);
        List<Product> onlineProducts = extractProductList(onlinePayload.get("products"));

        data.put("cf", cfProducts);
        data.put("cb", cbProducts);
        data.put("hybrid", hybridProducts);
        data.put("hot", hotProducts);
        Map<String, Object> optimizedQuality = buildRecommendationQualityReport(userId, onlineProducts);
        data.put("online", onlineProducts);
        data.put("quality", optimizedQuality);
        data.put("portraitLayers", buildRecommendationPortraitLayers(userId, onlineProducts));
        data.put("explainableFormula", buildExplainableFormula());
        data.put("beforeAfterQuality", buildBeforeAfterRecommendationQuality(userId, hotProducts, onlineProducts, optimizedQuality));

        data.put("experimentGroup", hybridDecision.getExperimentGroup() != null
                ? hybridDecision.getExperimentGroup()
                : ABTestFramework.ExperimentGroup.HYBRID.code);
        data.put("weights", hybridDecision.getAlgorithmWeights() == null || hybridDecision.getAlgorithmWeights().isEmpty()
                ? hybridEngine.getDefaultWeights()
                : hybridDecision.getAlgorithmWeights());
        Map<String, Object> algorithmFallback = new LinkedHashMap<>();
        algorithmFallback.put("cf", false);
        algorithmFallback.put("cb", false);
        data.put("algorithmFallback", algorithmFallback);
        Map<String, Object> unavailableReasons = new LinkedHashMap<>();
        if (cfProducts.isEmpty()) {
            unavailableReasons.put("cf", "缺少相似用户或协同过滤候选，未用热门/混合结果冒充。");
        }
        if (cbProducts.isEmpty()) {
            unavailableReasons.put("cb", "缺少内容相似候选，未用热门/混合结果冒充。");
        }
        data.put("unavailableReasons", unavailableReasons);

        return Result.success(data);
    }

    @GetMapping("/recommend/user-profile/{userId}")
    public Result<?> userProfile(@PathVariable Long userId) {
        requireModuleEnabled("recommendation");
        userPreferenceBootstrapService.ensureUserPreferenceInitialized(userId, false);
        ensureSegmentCoverageForUser(userId);
        Map<String, Object> profile = new LinkedHashMap<>();

        User user = userService.getById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        profile.put("userId", userId);
        String displayName = (user.getNickname() != null && !user.getNickname().isEmpty())
                ? user.getNickname() : user.getUsername();
        profile.put("username", displayName);
        profile.put("avatar", user.getAvatar());

        List<Map<String, Object>> behaviorStats = behaviorMapper.selectUserBehaviorStats(userId);
        profile.put("behaviorStats", behaviorStats);

        List<Map<String, Object>> preferences = behaviorMapper.selectUserPreferences(userId);
        List<Map<String, Object>> searchCategoryPreferences = safeSearchCategoryPreferences(userId, 8);
        profile.put("preferences", preferences);
        profile.put("searchCategoryPreferences", searchCategoryPreferences);
        profile.put("portraitLayers", buildRecommendationPortraitLayers(userId, safePortraitProducts(userId)));
        profile.put("explainableFormula", buildExplainableFormula());

        Map<Long, Double> userVector = collaborativeFiltering.buildUserVector(userId);
        profile.put("interactedProducts", userVector.size());
        profile.put("vectorDimension", userVector.size());

        Map<String, Double> categoryWeights = new LinkedHashMap<>();
        Set<String> userTags = new LinkedHashSet<>();
        for (Map<String, Object> pref : preferences) {
            Object catId = pref.get("category_id");
            Object weight = pref.get("weight");
            Object tags = pref.get("tags");
            if (catId != null && weight != null) {
                categoryWeights.merge(catId.toString(), Double.parseDouble(weight.toString()), Double::sum);
            }
            if (tags != null) {
                for (String tag : tags.toString().replace("[", "").replace("]", "").replace("\"", "").split(",")) {
                    tag = tag.trim();
                    if (!tag.isEmpty()) userTags.add(tag);
                }
            }
        }
        for (Map<String, Object> pref : searchCategoryPreferences) {
            Object catName = firstPresent(pref.get("category_name"), pref.get("categoryName"));
            Object catId = firstPresent(pref.get("category_id"), pref.get("categoryId"));
            Object weight = firstPresent(pref.get("weight"), pref.get("preferenceScore"));
            String label = catName != null ? String.valueOf(catName) : String.valueOf(catId);
            if (label != null && !label.isBlank() && weight != null) {
                categoryWeights.merge(label, readBigDecimal(weight).doubleValue(), Double::sum);
            }
        }
        profile.put("categoryWeights", categoryWeights);
        profile.put("userTags", userTags);

        profile.put("experimentGroup", ABTestFramework.ExperimentGroup.HYBRID.code);
        profile.put("experimentGroupDesc", "标准混合组（按用户稳定分桶）");

        return Result.success(profile);
    }

    @PostMapping("/recommend/profile-bootstrap")
    public Result<?> rebuildUserPreferences(@RequestParam(defaultValue = "false") boolean force,
                                            @RequestParam(defaultValue = "0") int limit) {
        requireModuleEnabled("recommendation");
        Map<String, Object> summary = userPreferenceBootstrapService.bootstrapAllUsers(force, limit);
        return Result.success(summary);
    }

    private void ensureSegmentCoverageForUser(Long userId) {
        try {
            kmeansCoverageBackfillService.ensureUserAssignedToLatestTask(userId);
        } catch (Exception exception) {
            // 分群回补失败不影响推荐主链路。
        }
    }

    @GetMapping("/recommend/abtest-report")
    public Result<?> abtestReport(HttpServletRequest request) {
        requireAdminRequest(request);
        moduleSwitchService.requireEnabled("ab-test");
        if (!moduleSwitchService.isEnabled("ab-test")) {
            return Result.success(Collections.emptyMap());
        }
        return Result.success(abTestFramework.getReport());
    }

    @PostMapping("/recommend/abtest-reset")
    public Result<?> abtestReset(HttpServletRequest request) {
        requireAdminRequest(request);
        moduleSwitchService.requireEnabled("ab-test");
        if (!moduleSwitchService.isEnabled("ab-test")) {
            return Result.success();
        }
        abTestFramework.resetMetrics();
        return Result.success("A/B测试数据已重置");
    }

    // ========== 用户资料修改审核 ==========

    @GetMapping("/profile-changes")
    public Result<?> listProfileChanges(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<ProfileChangeRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ProfileChangeRequest::getStatus, status);
        }
        wrapper.orderByDesc(ProfileChangeRequest::getCreateTime);
        Page<ProfileChangeRequest> result = profileChangeRequestMapper.selectPage(
                new Page<>(page, size), wrapper);

        List<Map<String, Object>> records = new ArrayList<>();
        for (ProfileChangeRequest r : result.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("userId", r.getUserId());
            User u = userService.getById(r.getUserId());
            item.put("currentNickname", u != null ? u.getNickname() : "");
            item.put("currentAvatar", u != null ? u.getAvatar() : "");
            item.put("newNickname", r.getNewNickname());
            item.put("newAvatar", r.getNewAvatar());
            item.put("status", r.getStatus());
            item.put("rejectReason", r.getRejectReason());
            item.put("createTime", r.getCreateTime());
            item.put("reviewTime", r.getReviewTime());
            records.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        return Result.success(data);
    }

    @Transactional
    @PostMapping("/profile-changes/{id}/approve")
    public Result<?> approveProfileChange(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        ProfileChangeRequest req = profileChangeRequestMapper.selectById(id);
        if (req == null) throw new BusinessException("审核记录不存在");
        if (req.getStatus() != 0) throw new BusinessException("该申请已处理");

        User update = new User();
        update.setId(req.getUserId());
        if (req.getNewNickname() != null) update.setNickname(req.getNewNickname());
        if (req.getNewAvatar() != null) update.setAvatar(req.getNewAvatar());
        update.setLastProfileChange(LocalDateTime.now());
        userService.updateById(update);

        req.setStatus(1);
        req.setReviewTime(LocalDateTime.now());
        req.setReviewerId(adminId);
        profileChangeRequestMapper.updateById(req);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "profile-change");
        payload.put("requestId", req.getId());
        payload.put("status", "approved");
        payload.put("userId", req.getUserId());
        managementWorkbenchRealtimeService.notifyAdmins("profile-change-reviewed", payload);

        return Result.success("已通过");
    }

    @PostMapping("/profile-changes/{id}/reject")
    public Result<?> rejectProfileChange(@PathVariable Long id,
                                          @RequestBody Map<String, String> params,
                                          HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        ProfileChangeRequest req = profileChangeRequestMapper.selectById(id);
        if (req == null) throw new BusinessException("审核记录不存在");
        if (req.getStatus() != 0) throw new BusinessException("该申请已处理");

        req.setStatus(2);
        req.setRejectReason(params.getOrDefault("reason", "不符合规范"));
        req.setReviewTime(LocalDateTime.now());
        req.setReviewerId(adminId);
        profileChangeRequestMapper.updateById(req);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "profile-change");
        payload.put("requestId", req.getId());
        payload.put("status", "rejected");
        payload.put("userId", req.getUserId());
        managementWorkbenchRealtimeService.notifyAdmins("profile-change-reviewed", payload);

        return Result.success("已拒绝");
    }

    // ==================== 功能开关管理 ====================

    @GetMapping("/role-permissions")
    public Result<?> getRolePermissions(HttpServletRequest request) {
        requireAdminPermission(request, "system.role.read");
        return Result.success(rolePermissionService.getRolePermissionOverview());
    }

    @GetMapping("/role-permissions/me")
    public Result<?> getCurrentRolePermissions(HttpServletRequest request) {
        requireAdminRequest(request);
        Object roleObj = request.getAttribute("role");
        String role = roleObj == null ? null : String.valueOf(roleObj);
        return Result.success(rolePermissionService.getCurrentRolePermissionView(role));
    }

    @PutMapping("/role-permissions/{role}")
    @Log(module = "系统管理", action = "修改角色权限")
    public Result<?> updateRolePermissions(@PathVariable String role,
                                           @RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        requireAdminPermission(request, "system.role.write");
        List<String> permissions = parseStringList(body == null ? null : body.get("permissions"));
        Map<String, Object> result = rolePermissionService.updateRolePermissions(role, permissions);
        return Result.success("角色权限更新成功", result);
    }

    @PutMapping("/role-permissions/{role}/reset")
    @Log(module = "系统管理", action = "重置角色权限")
    public Result<?> resetRolePermissions(@PathVariable String role,
                                          HttpServletRequest request) {
        requireAdminPermission(request, "system.role.write");
        Map<String, Object> result = rolePermissionService.resetRolePermissions(role);
        return Result.success("角色权限已恢复默认配置", result);
    }

    @GetMapping("/module-switches")
    public Result<?> getAllModuleSwitches(HttpServletRequest request) {
        requireAdminPermission(request, "system.module.read");
        return Result.success(moduleSwitchService.getAllSwitches());
    }

    @GetMapping("/module-switches/summary")
    public Result<?> getModuleSwitchSummary(HttpServletRequest request) {
        requireAdminPermission(request, "system.module.read");
        return Result.success(moduleSwitchService.getSwitchSummary());
    }

    @PutMapping("/module-switches")
    @Log(module = "系统管理", action = "修改功能开关")
    public Result<?> updateModuleSwitch(@RequestBody Map<String, Object> body,
                                        HttpServletRequest request) {
        requireAdminPermission(request, "system.module.write");
        String module = (String) body.get("module");
        Object enabledObj = body.get("enabled");
        if (module == null || enabledObj == null) {
            return Result.error("参数不完整");
        }
        boolean enabled = Boolean.parseBoolean(enabledObj.toString());
        boolean force = Boolean.parseBoolean(String.valueOf(body.getOrDefault("force", false)));
        boolean autoEnableDependencies = Boolean.parseBoolean(String.valueOf(body.getOrDefault("autoEnableDependencies", false)));
        Map<String, Object> result = moduleSwitchService.applySwitch(module, enabled, force, autoEnableDependencies);
        if (Boolean.TRUE.equals(result.get("blocked"))) {
            return Result.error(409, String.valueOf(result.get("message")));
        }
        return Result.success(String.valueOf(result.get("message")), result);
    }

    @PutMapping("/module-switches/batch")
    @Log(module = "系统管理", action = "批量修改功能开关")
    public Result<?> batchUpdateModuleSwitches(@RequestBody Map<String, Object> body,
                                               HttpServletRequest request) {
        requireAdminPermission(request, "system.module.write");
        Map<String, Boolean> switches = new LinkedHashMap<>();
        Object nestedSwitches = body.get("switches");
        if (nestedSwitches instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) nestedSwitches).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    continue;
                }
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                switches.put((String) entry.getKey(), Boolean.parseBoolean(String.valueOf(value)));
            }
        } else {
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                if ("switches".equals(entry.getKey())
                        || "force".equals(entry.getKey())
                        || "autoEnableDependencies".equals(entry.getKey())) {
                    continue;
                }
                if (entry.getValue() == null) {
                    continue;
                }
                switches.put(entry.getKey(), Boolean.parseBoolean(String.valueOf(entry.getValue())));
            }
        }

        boolean force = Boolean.parseBoolean(String.valueOf(body.getOrDefault("force", false)));
        boolean autoEnableDependencies = Boolean.parseBoolean(String.valueOf(body.getOrDefault("autoEnableDependencies", false)));
        Map<String, Object> result = moduleSwitchService.applyBatch(switches, force, autoEnableDependencies);
        if (Boolean.TRUE.equals(result.get("blocked"))) {
            return Result.error(409, String.valueOf(result.get("message")));
        }
        return Result.success(String.valueOf(result.get("message")), result);
    }

    private void ensureNonAdminUser(Long userId) {
        User existing = userService.getById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        if (Constants.Role.ADMIN.equals(existing.getRole())) {
            throw new BusinessException("系统管理员账号不允许执行此操作");
        }
    }

    private List<Map<String, Object>> loadBehaviorAnalyticsSnapshot() {
        try {
            AnalyticsBehaviorDaily latest = analyticsBehaviorDailyMapper.selectOne(
                    new LambdaQueryWrapper<AnalyticsBehaviorDaily>()
                            .orderByDesc(AnalyticsBehaviorDaily::getStatDate)
                            .last("LIMIT 1"));
            if (latest == null || latest.getStatDate() == null) {
                return null;
            }

            LocalDate statDate = latest.getStatDate();
            List<AnalyticsBehaviorDaily> rows = analyticsBehaviorDailyMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsBehaviorDaily>()
                            .eq(AnalyticsBehaviorDaily::getStatDate, statDate)
                            .orderByAsc(AnalyticsBehaviorDaily::getBehaviorType));
            if (rows.isEmpty()) {
                return null;
            }

            List<Map<String, Object>> stats = new ArrayList<>();
            for (AnalyticsBehaviorDaily row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("behavior_type", row.getBehaviorType());
                item.put("count", row.getEventCount() != null ? row.getEventCount() : 0L);
                item.put("user_count", row.getUserCount() != null ? row.getUserCount() : 0L);
                item.put("stat_date", statDate.toString());
                stats.add(item);
            }
            return stats;
        } catch (Exception exception) {
            return null;
        }
    }

    private Coupon prepareCouponForSave(Coupon coupon, Coupon existing) {
        if (coupon == null && existing == null) {
            return null;
        }

        Coupon target = new Coupon();
        if (existing != null) {
            target.setId(existing.getId());
            target.setCreateTime(existing.getCreateTime());
        }

        target.setName(firstNonBlank(coupon == null ? null : coupon.getName(),
                existing == null ? null : existing.getName()));
        target.setType(firstNonNull(coupon == null ? null : coupon.getType(),
                existing == null ? null : existing.getType()));
        target.setValue(firstNonNull(coupon == null ? null : coupon.getValue(),
                existing == null ? null : existing.getValue()));
        target.setMinAmount(firstNonNull(coupon == null ? null : coupon.getMinAmount(),
                existing == null ? BigDecimal.ZERO : existing.getMinAmount(),
                BigDecimal.ZERO));
        target.setMaxDiscount(firstNonNull(coupon == null ? null : coupon.getMaxDiscount(),
                existing == null ? null : existing.getMaxDiscount()));
        target.setTotalCount(firstNonNull(coupon == null ? null : coupon.getTotalCount(),
                existing == null ? null : existing.getTotalCount()));
        target.setUsedCount(firstNonNull(coupon == null ? null : coupon.getUsedCount(),
                existing == null ? 0 : existing.getUsedCount(),
                0));
        target.setStartTime(firstNonNull(coupon == null ? null : coupon.getStartTime(),
                existing == null ? null : existing.getStartTime()));
        target.setEndTime(firstNonNull(coupon == null ? null : coupon.getEndTime(),
                existing == null ? null : existing.getEndTime()));
        target.setStatus(firstNonNull(coupon == null ? null : coupon.getStatus(),
                existing == null ? 1 : existing.getStatus(),
                1));
        target.setScopeType(firstNonNull(coupon == null ? null : coupon.getScopeType(),
                existing == null ? Constants.CouponScope.PLATFORM : existing.getScopeType(),
                Constants.CouponScope.PLATFORM));
        target.setMerchantId(firstNonNull(coupon == null ? null : coupon.getMerchantId(),
                existing == null ? null : existing.getMerchantId()));
        target.setAudienceType(couponAudienceService.normalizeAudienceType(firstNonNull(
                coupon == null ? null : coupon.getAudienceType(),
                existing == null ? CouponAudienceService.AUDIENCE_ALL : existing.getAudienceType(),
                CouponAudienceService.AUDIENCE_ALL)));

        String audienceNote = firstNonBlank(
                coupon == null ? null : coupon.getAudienceNote(),
                existing == null ? null : existing.getAudienceNote());
        target.setAudienceNote(audienceNote == null ? "" : audienceNote.trim());

        if (target.getType() != null && target.getType() != 2) {
            target.setMaxDiscount(null);
        }
        if (target.getScopeType() == null || target.getScopeType() != Constants.CouponScope.MERCHANT_STORE) {
            target.setScopeType(Constants.CouponScope.PLATFORM);
            target.setMerchantId(null);
        }

        if (target.getAudienceType() == CouponAudienceService.AUDIENCE_SEGMENT) {
            target.setTargetSegmentCodes(couponAudienceService.normalizeSegmentCodeList(firstNonBlank(
                    coupon == null ? null : coupon.getTargetSegmentCodes(),
                    existing == null ? null : existing.getTargetSegmentCodes())));
            target.setTargetUserIds("");
        } else if (target.getAudienceType() == CouponAudienceService.AUDIENCE_USER) {
            target.setTargetSegmentCodes("");
            target.setTargetUserIds(couponAudienceService.normalizeTargetUserIdList(firstNonBlank(
                    coupon == null ? null : coupon.getTargetUserIds(),
                    existing == null ? null : existing.getTargetUserIds())));
        } else {
            target.setTargetSegmentCodes("");
            target.setTargetUserIds("");
        }

        return target;
    }

    private void validateCouponPayload(Coupon coupon) {
        if (coupon == null) {
            throw new BusinessException("优惠券参数不能为空");
        }
        if (!org.springframework.util.StringUtils.hasText(coupon.getName())) {
            throw new BusinessException("优惠券名称不能为空");
        }
        if (coupon.getType() == null || (coupon.getType() != 1 && coupon.getType() != 2 && coupon.getType() != 3)) {
            throw new BusinessException("优惠券类型不合法");
        }
        if (coupon.getValue() == null || coupon.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("优惠券面额或折扣值必须大于0");
        }
        if (coupon.getMinAmount() == null || coupon.getMinAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("优惠券门槛金额不能小于0");
        }
        if (coupon.getType() == 2
                && coupon.getMaxDiscount() != null
                && coupon.getMaxDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("折扣券最大优惠金额必须大于0");
        }
        if (coupon.getTotalCount() == null || coupon.getTotalCount() <= 0) {
            throw new BusinessException("优惠券库存必须大于0");
        }
        if (coupon.getUsedCount() == null
                || coupon.getUsedCount() < 0
                || coupon.getUsedCount() > coupon.getTotalCount()) {
            throw new BusinessException("已发放数量不合法");
        }
        if (coupon.getStartTime() == null
                || coupon.getEndTime() == null
                || !coupon.getEndTime().isAfter(coupon.getStartTime())) {
            throw new BusinessException("优惠券有效期配置不合法");
        }
        if (coupon.getStatus() == null || (coupon.getStatus() != 0 && coupon.getStatus() != 1 && coupon.getStatus() != 2)) {
            throw new BusinessException("优惠券状态不合法");
        }
        if (coupon.getScopeType() == null
                || (coupon.getScopeType() != Constants.CouponScope.PLATFORM
                && coupon.getScopeType() != Constants.CouponScope.MERCHANT_STORE)) {
            throw new BusinessException("优惠券作用域不合法");
        }
        if (coupon.getScopeType() == Constants.CouponScope.MERCHANT_STORE && coupon.getMerchantId() == null) {
            throw new BusinessException("商家券必须指定商家ID");
        }
        if (coupon.getAudienceType() == CouponAudienceService.AUDIENCE_SEGMENT
                && couponAudienceService.parseSegmentCodes(coupon.getTargetSegmentCodes()).isEmpty()) {
            throw new BusinessException("分群定向券至少需要配置一个分群编码");
        }
        if (coupon.getAudienceType() == CouponAudienceService.AUDIENCE_USER
                && couponAudienceService.parseTargetUserIds(coupon.getTargetUserIds()).isEmpty()) {
            throw new BusinessException("指定用户券至少需要配置一个用户 ID");
        }
    }

    private void validateDiscountCouponValue(Coupon coupon) {
        Integer effectiveType = coupon == null ? null : coupon.getType();
        BigDecimal effectiveValue = coupon == null ? null : coupon.getValue();
        if (effectiveType != null
                && effectiveType == 2
                && effectiveValue != null
                && effectiveValue.compareTo(new BigDecimal("10")) >= 0) {
                throw new BusinessException("折扣券折扣值必须小于 10");
        }
    }

    private void validateCouponPayload(Coupon coupon, Coupon existing) {
        if (coupon == null) {
            throw new BusinessException("优惠券参数不能为空");
        }

        String name = firstNonBlank(coupon.getName(), existing == null ? null : existing.getName());
        Integer type = firstNonNull(coupon.getType(), existing == null ? null : existing.getType());
        BigDecimal value = firstNonNull(coupon.getValue(), existing == null ? null : existing.getValue());
        BigDecimal minAmount = firstNonNull(coupon.getMinAmount(), existing == null ? BigDecimal.ZERO : existing.getMinAmount());
        BigDecimal maxDiscount = firstNonNull(coupon.getMaxDiscount(), existing == null ? null : existing.getMaxDiscount());
        Integer totalCount = firstNonNull(coupon.getTotalCount(), existing == null ? null : existing.getTotalCount());
        Integer usedCount = firstNonNull(coupon.getUsedCount(), existing == null ? 0 : existing.getUsedCount());
        Integer status = firstNonNull(coupon.getStatus(), existing == null ? 1 : existing.getStatus());
        LocalDateTime startTime = firstNonNull(coupon.getStartTime(), existing == null ? null : existing.getStartTime());
        LocalDateTime endTime = firstNonNull(coupon.getEndTime(), existing == null ? null : existing.getEndTime());

        if (!org.springframework.util.StringUtils.hasText(name)) {
            throw new BusinessException("优惠券名称不能为空");
        }
        if (type == null || (type != 1 && type != 2 && type != 3)) {
            throw new BusinessException("优惠券类型不合法");
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("优惠券面额或折扣值必须大于0");
        }
        if (minAmount == null || minAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("优惠券门槛金额不能小于0");
        }
        if (type == 2 && maxDiscount != null && maxDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("折扣券最大优惠金额必须大于0");
        }
        if (totalCount == null || totalCount <= 0) {
            throw new BusinessException("优惠券库存必须大于0");
        }
        if (usedCount == null || usedCount < 0 || usedCount > totalCount) {
            throw new BusinessException("已发放数量不合法");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException("优惠券有效期配置不合法");
        }
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BusinessException("优惠券状态不合法");
        }

        if (coupon.getMinAmount() == null && existing == null) {
            coupon.setMinAmount(BigDecimal.ZERO);
        }
        if (coupon.getStatus() == null && existing == null) {
            coupon.setStatus(1);
        }
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (org.springframework.util.StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void validateDiscountCouponValue(Coupon coupon, Coupon existing) {
        Integer effectiveType = firstNonNull(
                coupon == null ? null : coupon.getType(),
                existing == null ? null : existing.getType()
        );
        BigDecimal effectiveValue = firstNonNull(
                coupon == null ? null : coupon.getValue(),
                existing == null ? null : existing.getValue()
        );
        if (effectiveType != null
                && effectiveType == 2
                && effectiveValue != null
                && effectiveValue.compareTo(new BigDecimal("10")) >= 0) {
                throw new BusinessException("折扣券折扣值必须小于 10");
        }
    }

    private Map<String, Object> buildSupportSummary(LocalDateTime startTime) {
        LocalDateTime now = LocalDateTime.now();

        long conversationCount = imConversationMapper.selectCount(
                new LambdaQueryWrapper<ImConversation>()
                        .ge(ImConversation::getCreateTime, startTime));
        long activeConversationCount = imConversationMapper.selectCount(
                new LambdaQueryWrapper<ImConversation>()
                        .ge(ImConversation::getUpdateTime, startTime));

        List<String> openStatuses = Arrays.asList("pending", "open", "processing", "assigned");
        long openTicketCount = imTicketMapper.selectCount(
                new LambdaQueryWrapper<ImTicket>()
                        .in(ImTicket::getTicketStatus, openStatuses));
        long resolvedTicketCount = imTicketMapper.selectCount(
                new LambdaQueryWrapper<ImTicket>()
                        .in(ImTicket::getTicketStatus, Arrays.asList("resolved", "closed")));
        long overdueTicketCount = imTicketMapper.selectCount(
                new LambdaQueryWrapper<ImTicket>()
                        .in(ImTicket::getTicketStatus, openStatuses)
                        .isNotNull(ImTicket::getSlaDeadlineTime)
                        .lt(ImTicket::getSlaDeadlineTime, now));
        long pendingTimeoutCount = imTicketMapper.selectCount(
                new LambdaQueryWrapper<ImTicket>()
                        .in(ImTicket::getTicketStatus, openStatuses)
                        .isNotNull(ImTicket::getCreateTime)
                        .lt(ImTicket::getCreateTime, now.minusMinutes(SUPPORT_PENDING_TIMEOUT_MINUTES)));
        long processingTimeoutCount = imTicketMapper.selectCount(
                new LambdaQueryWrapper<ImTicket>()
                        .in(ImTicket::getTicketStatus, Arrays.asList("processing", "assigned"))
                        .isNotNull(ImTicket::getUpdateTime)
                        .lt(ImTicket::getUpdateTime, now.minusMinutes(SUPPORT_PROCESSING_TIMEOUT_MINUTES)));
        long messageCount = imMessageMapper.selectCount(
                new LambdaQueryWrapper<ImMessage>()
                        .ge(ImMessage::getCreateTime, startTime));

        Map<String, Object> supportSummary = new LinkedHashMap<>();
        supportSummary.put("conversationCount", conversationCount);
        supportSummary.put("activeConversationCount", activeConversationCount);
        supportSummary.put("messageCount", messageCount);
        supportSummary.put("openTicketCount", openTicketCount);
        supportSummary.put("resolvedTicketCount", resolvedTicketCount);
        supportSummary.put("overdueTicketCount", overdueTicketCount);
        supportSummary.put("pendingTimeoutCount", pendingTimeoutCount);
        supportSummary.put("processingTimeoutCount", processingTimeoutCount);
        supportSummary.put("resolvedRate", percent(resolvedTicketCount, openTicketCount + resolvedTicketCount));
        supportSummary.put("overdueRate", percent(overdueTicketCount, openTicketCount));
        return supportSummary;
    }

    private List<Map<String, Object>> normalizeRecommendationDaily(List<Map<String, Object>> rawDailyMetrics) {
        if (rawDailyMetrics == null || rawDailyMetrics.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rawDailyMetrics) {
            Map<String, Object> item = new LinkedHashMap<>();
            long exposureCount = readLong(row == null ? null : row.get("exposureCount"));
            long clickCount = readLong(row == null ? null : row.get("clickCount"));
            long purchaseCount = readLong(row == null ? null : row.get("purchaseCount"));
            item.put("statDate", row == null ? null : row.get("statDate"));
            item.put("exposureCount", exposureCount);
            item.put("clickCount", clickCount);
            item.put("purchaseCount", purchaseCount);
            item.put("ctr", percent(clickCount, exposureCount));
            item.put("cvr", percent(purchaseCount, exposureCount));
            normalized.add(item);
        }
        return normalized;
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf((double) numerator * 100D / (double) denominator)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    private List<Product> extractProductList(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<Product> products = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Product) {
                products.add((Product) item);
            }
        }
        return products;
    }

    private List<Product> safePortraitProducts(Long userId) {
        try {
            Map<String, Object> payload = recommendationService.getPersonalRecommendationsWithExplanation(userId, 10, true);
            return extractProductList(payload.get("products"));
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> buildRecommendationQualityReport(Long userId, List<Product> products) {
        Map<String, Object> report = new LinkedHashMap<>();
        List<String> topCategories = loadAdminTopPreferenceCategories(userId, 2);
        report.put("topPreferenceCategories", topCategories);
        int inspectSize = Math.min(10, products == null ? 0 : products.size());
        if (inspectSize <= 0 || topCategories.isEmpty()) {
            report.put("inspectSize", inspectSize);
            report.put("topCategoryHitCount", 0);
            report.put("topCategoryHitRate", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            report.put("status", "NO_PROFILE_OR_RESULT");
            report.put("message", "缺少用户画像或推荐结果，无法计算偏好命中率。");
            return report;
        }

        Set<String> topSet = topCategories.stream()
                .map(this::normalizeCategoryLabel)
                .filter(label -> !label.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Integer> distribution = new LinkedHashMap<>();
        int hitCount = 0;
        for (int i = 0; i < inspectSize; i++) {
            Product product = products.get(i);
            String category = normalizeCategoryLabel(product == null ? null : product.getCategoryName());
            if (category.isEmpty() && product != null && product.getCategoryId() != null) {
                category = normalizeCategoryLabel(resolveAdminCategoryName(product.getCategoryId()));
            }
            distribution.merge(category.isEmpty() ? "未分类" : category, 1, Integer::sum);
            if (topSet.contains(category)) {
                hitCount++;
            }
        }
        BigDecimal hitRate = percent(hitCount, inspectSize);
        report.put("inspectSize", inspectSize);
        report.put("topCategoryHitCount", hitCount);
        report.put("topCategoryHitRate", hitRate);
        report.put("categoryDistribution", distribution);
        report.put("tagComparisons", buildTagComparisons(products, inspectSize, topCategories));
        report.put("status", hitRate.compareTo(BigDecimal.valueOf(60)) >= 0 ? "PASS" : "LOW_MATCH");
        report.put("message", hitRate.compareTo(BigDecimal.valueOf(60)) >= 0
                ? "最终上线排序已满足 Top 偏好品类占比要求。"
                : "最终上线排序 Top 偏好命中不足，应检查候选池是否缺少对应品类商品。");
        return report;
    }

    private Map<String, Object> buildRecommendationPortraitLayers(Long userId, List<Product> products) {
        Map<String, Object> layers = new LinkedHashMap<>();
        List<String> longTerm = loadAdminTopPreferenceCategories(userId, 5);
        List<String> shortTerm = safeSearchCategoryPreferences(userId, 5).stream()
                .map(row -> firstPresent(row.get("category_name"), row.get("categoryName")))
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());

        layers.put("longTermInterest", buildPortraitLayer(
                "长期兴趣", longTerm, "订单、加购、收藏、浏览累计形成的稳定偏好"));
        layers.put("shortTermIntent", buildPortraitLayer(
                "短期意图", shortTerm, "近期搜索词映射到商品类目，代表当前购买意向"));
        layers.put("pricePreference", buildPortraitLayer(
                "价格偏好", inferPriceBands(products), "按命中商品价格带解释用户可接受价位"));
        layers.put("scenePreference", buildPortraitLayer(
                "场景偏好", inferSceneTags(products), "从商品细分标签提取通勤、户外、送礼、居家等使用场景"));
        return layers;
    }

    private Map<String, Object> buildExplainableFormula() {
        Map<String, Object> formula = new LinkedHashMap<>();
        formula.put("name", "可解释混合推荐");
        formula.put("expression", "短期意图 30% + 长期兴趣 25% + 协同过滤 30% + 热门探索 15%");
        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put("shortTermIntent", 0.30D);
        weights.put("longTermInterest", 0.25D);
        weights.put("collaborativeFiltering", 0.30D);
        weights.put("hotExploration", 0.15D);
        formula.put("weights", weights);
        formula.put("result", "先保证当前意图和长期画像一致，再保留相似用户和少量热门探索，避免推荐结果完全变成热榜。");
        return formula;
    }

    private Map<String, Object> buildBeforeAfterRecommendationQuality(Long userId,
                                                                      List<Product> baselineProducts,
                                                                      List<Product> optimizedProducts,
                                                                      Map<String, Object> optimizedQuality) {
        Map<String, Object> baselineQuality = buildRecommendationQualityReport(userId, baselineProducts);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baselineName", "旧口径：热门排序");
        result.put("optimizedName", "新口径：画像分层混合推荐");
        result.put("baseline", summarizeQuality(baselineQuality));
        result.put("optimized", summarizeQuality(optimizedQuality));
        result.put("hitRateLift", percentLift(readPercentValue(optimizedQuality.get("topCategoryHitRate")),
                readPercentValue(baselineQuality.get("topCategoryHitRate"))));
        result.put("tagMatchLift", percentLift(tagMatchRate(optimizedQuality), tagMatchRate(baselineQuality)));
        result.put("negativeFeedback", buildNegativeFeedbackComparison(userId, baselineProducts, optimizedProducts));
        result.put("conclusion", "对比 Top 偏好命中率和标签命中率，展示优化后是否更贴合用户画像。");
        return result;
    }

    private Map<String, Object> buildNegativeFeedbackComparison(Long userId,
                                                                List<Product> baselineProducts,
                                                                List<Product> optimizedProducts) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<UserBehavior> dislikes = behaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                .eq(UserBehavior::getUserId, userId)
                .eq(UserBehavior::getBehaviorType, Constants.BehaviorType.DISLIKE)
                .isNotNull(UserBehavior::getProductId)
                .ge(UserBehavior::getCreateTime, LocalDateTime.now().minusDays(30))
                .orderByDesc(UserBehavior::getCreateTime)
                .last("LIMIT 100"));
        Set<Long> dislikedIds = dislikes == null ? Collections.emptySet() : dislikes.stream()
                .map(UserBehavior::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> dislikedCategoryIds = Collections.emptySet();
        if (!dislikedIds.isEmpty()) {
            List<Product> dislikedProducts = productService.getProductsByIds(new ArrayList<>(dislikedIds));
            dislikedCategoryIds = dislikedProducts.stream()
                    .map(Product::getCategoryId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        int baselineProductHits = countProductHits(baselineProducts, dislikedIds);
        int optimizedProductHits = countProductHits(optimizedProducts, dislikedIds);
        int baselineCategoryHits = countCategoryHits(baselineProducts, dislikedCategoryIds);
        int optimizedCategoryHits = countCategoryHits(optimizedProducts, dislikedCategoryIds);

        result.put("lookbackDays", 30);
        result.put("dislikedProductCount", dislikedIds.size());
        result.put("dislikedCategoryCount", dislikedCategoryIds.size());
        result.put("baselineProductHits", baselineProductHits);
        result.put("optimizedProductHits", optimizedProductHits);
        result.put("productHitReduction", baselineProductHits - optimizedProductHits);
        result.put("baselineCategoryHits", baselineCategoryHits);
        result.put("optimizedCategoryHits", optimizedCategoryHits);
        result.put("categoryHitReduction", baselineCategoryHits - optimizedCategoryHits);
        result.put("conclusion", dislikedIds.isEmpty()
                ? "近 30 天暂无不感兴趣样本，可先在小程序长按商品触发负反馈。"
                : "新排序会优先压低用户明确不感兴趣的商品和相关类目。");
        return result;
    }

    private int countProductHits(List<Product> products, Set<Long> productIds) {
        if (products == null || products.isEmpty() || productIds == null || productIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Product product : products) {
            if (product != null && product.getId() != null && productIds.contains(product.getId())) {
                count++;
            }
        }
        return count;
    }

    private int countCategoryHits(List<Product> products, Set<Long> categoryIds) {
        if (products == null || products.isEmpty() || categoryIds == null || categoryIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Product product : products) {
            if (product != null && product.getCategoryId() != null && categoryIds.contains(product.getCategoryId())) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Object> summarizeQuality(Map<String, Object> quality) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (quality == null) {
            summary.put("topCategoryHitRate", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            summary.put("tagMatchRate", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            summary.put("topCategories", Collections.emptyList());
            return summary;
        }
        summary.put("topCategoryHitRate", quality.get("topCategoryHitRate"));
        summary.put("tagMatchRate", tagMatchRate(quality));
        summary.put("topCategories", quality.get("topPreferenceCategories"));
        summary.put("distribution", quality.get("categoryDistribution"));
        return summary;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal tagMatchRate(Map<String, Object> quality) {
        if (quality == null || !(quality.get("tagComparisons") instanceof List<?>)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<?> comparisons = (List<?>) quality.get("tagComparisons");
        if (comparisons.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long matched = comparisons.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> (Map<String, Object>) item)
                .filter(item -> item.get("matchedTags") instanceof List<?> && !((List<?>) item.get("matchedTags")).isEmpty())
                .count();
        return percent(matched, comparisons.size());
    }

    private BigDecimal percentLift(BigDecimal optimized, BigDecimal baseline) {
        return optimized.subtract(baseline).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal readPercentValue(Object value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception exception) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private Map<String, Object> buildPortraitLayer(String label, List<String> values, String basis) {
        Map<String, Object> layer = new LinkedHashMap<>();
        layer.put("label", label);
        layer.put("values", values == null ? Collections.emptyList() : values);
        layer.put("basis", basis);
        return layer;
    }

    private List<String> inferPriceBands(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Long> bands = products.stream()
                .filter(Objects::nonNull)
                .map(product -> priceBand(product.getPrice()))
                .filter(value -> !value.isBlank())
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));
        return bands.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String priceBand(BigDecimal price) {
        if (price == null) {
            return "";
        }
        double value = price.doubleValue();
        if (value <= 50D) {
            return "平价";
        }
        if (value <= 300D) {
            return "中档";
        }
        if (value <= 1000D) {
            return "品质";
        }
        return "高端";
    }

    private List<String> inferSceneTags(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Long> sceneCounts = new LinkedHashMap<>();
        for (Product product : products) {
            if (product == null || product.getTags() == null) {
                continue;
            }
            for (String tag : product.getTags()) {
                String text = String.valueOf(tag);
                if (text.startsWith("场景:") || text.startsWith("层级:")) {
                    sceneCounts.merge(text, 1L, Long::sum);
                }
            }
        }
        return sceneCounts.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildTagComparisons(List<Product> products, int inspectSize, List<String> topCategories) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> topSet = topCategories == null ? Collections.emptySet() : topCategories.stream()
                .map(this::normalizeCategoryLabel)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> comparisons = new ArrayList<>();
        for (int i = 0; i < Math.min(inspectSize, products.size()); i++) {
            Product product = products.get(i);
            if (product == null) {
                continue;
            }
            List<String> productTags = product.getTags() == null ? Collections.emptyList() : product.getTags();
            List<String> matchedTags = new ArrayList<>();
            String category = normalizeCategoryLabel(product.getCategoryName());
            if (category.isEmpty() && product.getCategoryId() != null) {
                category = normalizeCategoryLabel(resolveAdminCategoryName(product.getCategoryId()));
            }
            if (topSet.contains(category)) {
                matchedTags.add("类目命中:" + (product.getCategoryName() == null ? category : product.getCategoryName()));
            }
            for (String tag : productTags) {
                String normalized = normalizeCategoryLabel(tag);
                boolean hit = topSet.stream().anyMatch(top -> normalized.contains(top) || top.contains(normalized));
                if (hit || normalized.startsWith("类目:") || normalized.startsWith("细分:") || normalized.startsWith("场景:")) {
                    matchedTags.add(tag);
                }
                if (matchedTags.size() >= 6) {
                    break;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", i + 1);
            item.put("productId", product.getId());
            item.put("productName", product.getName());
            item.put("category", product.getCategoryName());
            item.put("matchedTags", matchedTags);
            item.put("basis", matchedTags.isEmpty()
                    ? "未命中画像标签，属于探索或候选补位"
                    : "命中画像标签，可解释为偏好一致推荐");
            comparisons.add(item);
        }
        return comparisons;
    }

    private List<String> loadAdminTopPreferenceCategories(Long userId, int limit) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        List<Map<String, Object>> preferences = behaviorMapper.selectUserPreferences(userId);
        if (preferences != null) {
            for (Map<String, Object> row : preferences) {
                Long categoryId = readLong(row.get("category_id"));
                String categoryName = resolveAdminCategoryName(categoryId);
                double weight = readBigDecimal(row.get("weight")).doubleValue();
                if (categoryName != null && !categoryName.isBlank() && weight > 0D) {
                    scores.merge(categoryName, weight, Double::sum);
                }
            }
        }
        for (Map<String, Object> row : safeSearchCategoryPreferences(userId, Math.max(8, limit * 4))) {
            Object categoryName = firstPresent(row.get("category_name"), row.get("categoryName"));
            double weight = readBigDecimal(firstPresent(row.get("weight"), row.get("preferenceScore"))).doubleValue();
            if (categoryName != null && !String.valueOf(categoryName).isBlank() && weight > 0D) {
                scores.merge(String.valueOf(categoryName), weight, Double::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> safeSearchCategoryPreferences(Long userId, int limit) {
        try {
            List<Map<String, Object>> rows = behaviorMapper.selectUserSearchCategoryPreferences(userId, Math.max(1, limit));
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private String resolveAdminCategoryName(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        return category == null ? String.valueOf(categoryId) : category.getName();
    }

    private String normalizeCategoryLabel(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Object firstPresent(Object first, Object second) {
        return first != null ? first : second;
    }

    private long readLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignore) {
            return BigDecimal.ZERO;
        }
    }

    private Object firstMapValue(List<Map<String, Object>> rows, String key) {
        if (rows == null || rows.isEmpty() || !org.springframework.util.StringUtils.hasText(key)) {
            return null;
        }
        Map<String, Object> first = rows.get(0);
        return first == null ? null : first.get(key);
    }

    private long countRetainedUsers(List<Long> cohortUserIds, LocalDateTime activeStart) {
        if (cohortUserIds == null || cohortUserIds.isEmpty()) {
            return 0L;
        }
        Set<Long> retainedUsers = new HashSet<>();
        int chunkSize = 500;
        for (int i = 0; i < cohortUserIds.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, cohortUserIds.size());
            List<Long> chunk = cohortUserIds.subList(i, end);
            List<Map<String, Object>> rows = behaviorMapper.selectMaps(
                    new QueryWrapper<UserBehavior>()
                            .select("DISTINCT user_id AS userId")
                            .in("user_id", chunk)
                            .ge("create_time", activeStart));
            for (Map<String, Object> row : rows) {
                long retainedUserId = readLong(row == null ? null : row.get("userId"));
                if (retainedUserId > 0) {
                    retainedUsers.add(retainedUserId);
                }
            }
        }
        return retainedUsers.size();
    }

    private void requireModuleEnabled(String module) {
        moduleSwitchService.requireEnabled(module);
    }

    private void requireAdminRequest(HttpServletRequest request) {
        Object role = request == null ? null : request.getAttribute("role");
        if (!(role instanceof String) || !Constants.Role.ADMIN.equalsIgnoreCase(((String) role).trim())) {
            throw new BusinessException("仅管理员可访问");
        }
    }

    private void requireAdminPermission(HttpServletRequest request, String permission) {
        requireAdminRequest(request);
        Object roleObj = request == null ? null : request.getAttribute("role");
        String role = roleObj == null ? null : String.valueOf(roleObj);
        if (!rolePermissionService.hasPermission(role, permission)) {
            throw BusinessException.forbidden("当前账号缺少权限: " + permission);
        }
    }

    private List<String> parseStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (!(value instanceof List<?>)) {
            return result;
        }
        for (Object item : (List<?>) value) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }
}
