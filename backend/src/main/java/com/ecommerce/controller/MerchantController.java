package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Log;
import com.ecommerce.common.Result;
import com.ecommerce.dto.MerchantProductBatchStatusDTO;
import com.ecommerce.dto.MerchantProductBatchStockDTO;
import com.ecommerce.entity.*;
import com.ecommerce.mapper.*;
import com.ecommerce.service.CouponAudienceService;
import com.ecommerce.service.ManagementWorkbenchBadgeService;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.ProductService;
import com.ecommerce.utils.RefundViewUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductReviewMapper reviewMapper;

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserBehaviorMapper userBehaviorMapper;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private AnalyticsKmeansTaskMapper analyticsKmeansTaskMapper;

    @Autowired
    private AnalyticsKmeansUserResultMapper analyticsKmeansUserResultMapper;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private CouponAudienceService couponAudienceService;

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @Autowired
    private ManagementWorkbenchBadgeService managementWorkbenchBadgeService;

    // ==================== 财务中心 ====================

    @GetMapping("/finance/stats")
    public Result<?> financeStats(HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        List<Product> products = productService.list(
                new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, merchantId));
        Set<Long> pids = products.stream().map(Product::getId).collect(Collectors.toSet());
        List<OrderItem> items = pids.isEmpty() ? Collections.emptyList() : orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getProductId, pids));
        Map<Long, BigDecimal> orderAmounts = groupOrderAmounts(items);
        Set<Long> orderIds = orderAmounts.keySet();

        List<Order> settledOrders = orderIds.isEmpty() ? Collections.emptyList() : orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .in(Order::getId, orderIds)
                        .in(Order::getStatus, Arrays.asList(
                                Constants.OrderStatus.PAID,
                                Constants.OrderStatus.SHIPPED,
                                Constants.OrderStatus.COMPLETED)));
        Set<Long> settledOrderIds = settledOrders.stream().map(Order::getId).collect(Collectors.toSet());

        BigDecimal totalRevenue = settledOrderIds.stream()
                .map(id -> orderAmounts.getOrDefault(id, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalOrders = settledOrderIds.size();
        long totalSales = items.stream()
                .filter(item -> settledOrderIds.contains(item.getOrderId()))
                .mapToLong(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
        BigDecimal avgOrderValue = totalOrders > 0 ? totalRevenue.divide(
                new BigDecimal(totalOrders), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        BigDecimal monthRevenue = settledOrders.stream()
                .filter(order -> !order.getCreateTime().isBefore(monthStart))
                .map(order -> orderAmounts.getOrDefault(order.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRevenue", totalRevenue);
        stats.put("monthRevenue", monthRevenue);
        stats.put("totalOrders", totalOrders);
        stats.put("avgOrderValue", avgOrderValue);
        stats.put("totalSales", totalSales);
        return Result.success(stats);
    }

    @GetMapping("/finance/details")
    public Result<?> financeDetails(HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        Long merchantId = (Long) request.getAttribute("userId");
        List<Product> products = productService.list(
                new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, merchantId));
        if (products.isEmpty()) {
            return Result.success(new Page<>(page, size));
        }

        Set<Long> pids = products.stream().map(Product::getId).collect(Collectors.toSet());
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getProductId, pids));
        Map<Long, BigDecimal> orderAmounts = groupOrderAmounts(items);
        Set<Long> orderIds = orderAmounts.keySet();
        if (orderIds.isEmpty()) {
            return Result.success(new Page<>(page, size));
        }

        IPage<Order> orderPage = orderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Order>().in(Order::getId, orderIds)
                        .in(Order::getStatus, Arrays.asList(
                                Constants.OrderStatus.PAID,
                                Constants.OrderStatus.SHIPPED,
                                Constants.OrderStatus.COMPLETED))
                        .orderByDesc(Order::getCreateTime));

        Map<Long, Long> orderItemCounts = items.stream()
                .filter(i -> orderIds.contains(i.getOrderId()))
                .collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.counting()));

        List<Map<String, Object>> records = new ArrayList<>();
        for (Order order : orderPage.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", order.getOrderNo());
            User u = userMapper.selectById(order.getUserId());
            row.put("username", u != null ? u.getUsername() : "-");
            row.put("amount", orderAmounts.getOrDefault(order.getId(), BigDecimal.ZERO));
            row.put("itemCount", orderItemCounts.getOrDefault(order.getId(), 0L));
            row.put("status", order.getStatus());
            row.put("createTime", order.getCreateTime());
            records.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", orderPage.getTotal());
        result.put("current", orderPage.getCurrent());
        return Result.success(result);
    }

    @GetMapping("/finance/trend")
    public Result<?> financeTrend(@RequestParam(defaultValue = "30") int days,
                                  HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        List<Product> products = productService.list(
                new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, merchantId));
        Set<Long> pids = products.stream().map(Product::getId).collect(Collectors.toSet());

        int safeDays = Math.max(7, Math.min(days, 180));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(safeDays).withHour(0).withMinute(0).withSecond(0);

        List<String> dates = new ArrayList<>();
        List<BigDecimal> revenues = new ArrayList<>();
        List<Integer> orderCounts = new ArrayList<>();

        if (!pids.isEmpty()) {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().in(OrderItem::getProductId, pids));
            Map<Long, BigDecimal> orderAmounts = groupOrderAmounts(items);
            Set<Long> orderIds = orderAmounts.keySet();

            List<Order> orders = orderIds.isEmpty() ? Collections.emptyList() :
                    orderMapper.selectList(new LambdaQueryWrapper<Order>()
                            .in(Order::getId, orderIds)
                            .ge(Order::getCreateTime, start)
                            .in(Order::getStatus, Arrays.asList(
                                    Constants.OrderStatus.PAID,
                                    Constants.OrderStatus.SHIPPED,
                                    Constants.OrderStatus.COMPLETED)));
            Map<String, BigDecimal> revenueByDate = new HashMap<>();
            Map<String, Integer> orderCountByDate = new HashMap<>();
            for (Order order : orders) {
                String dateKey = order.getCreateTime().toLocalDate().toString();
                revenueByDate.merge(dateKey, orderAmounts.getOrDefault(order.getId(), BigDecimal.ZERO), BigDecimal::add);
                orderCountByDate.merge(dateKey, 1, Integer::sum);
            }

            for (int i = safeDays - 1; i >= 0; i--) {
                String date = now.minusDays(i).toLocalDate().toString();
                dates.add(date.substring(5));
                revenues.add(revenueByDate.getOrDefault(date, BigDecimal.ZERO));
                orderCounts.add(orderCountByDate.getOrDefault(date, 0));
            }
        } else {
            for (int i = safeDays - 1; i >= 0; i--) {
                dates.add(now.minusDays(i).toLocalDate().toString().substring(5));
                revenues.add(BigDecimal.ZERO);
                orderCounts.add(0);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("revenues", revenues);
        result.put("orderCounts", orderCounts);
        result.put("days", safeDays);
        return Result.success(result);
    }

    @GetMapping("/dashboard")
    public Result<?> dashboard(HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        IPage<Product> products = productService.getMerchantProducts(merchantId, 1, 9999);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProducts", products.getTotal());

        long totalSales = products.getRecords().stream().mapToLong(Product::getSalesCount).sum();
        stats.put("totalSales", totalSales);

        BigDecimal totalRevenue = products.getRecords().stream()
                .map(p -> p.getPrice().multiply(new BigDecimal(p.getSalesCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalRevenue", totalRevenue);

        long onShelf = products.getRecords().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == Constants.ProductStatus.ON_SHELF).count();
        stats.put("onShelfCount", onShelf);
        stats.put("offShelfCount", products.getTotal() - onShelf);

        List<Product> topProducts = products.getRecords().stream()
                .sorted((a, b) -> b.getSalesCount().compareTo(a.getSalesCount()))
                .limit(5).collect(Collectors.toList());
        stats.put("topProducts", topProducts);

        List<Product> lowStock = products.getRecords().stream()
                .filter(p -> p.getStock() != null && p.getStock() < 50)
                .collect(Collectors.toList());
        stats.put("lowStockProducts", lowStock);

        return Result.success(stats);
    }

    @GetMapping("/workbench/badge-counts")
    public Result<?> workbenchBadgeCounts(@RequestParam(required = false) String scope,
                                          HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        List<String> scopes = managementWorkbenchBadgeService.parseScopes(scope);
        return Result.success(managementWorkbenchBadgeService.getMerchantBadgeCounts(merchantId, scopes));
    }

    // ==================== 商家行为分析 ====================

    @GetMapping("/analytics/behavior")
    public Result<?> behaviorAnalytics(@RequestParam(defaultValue = "30") int days,
                                       HttpServletRequest request) {
        if (!moduleSwitchService.isEnabled("recommendation")) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("behaviorStats", Collections.emptyList());
            empty.put("segmentDistribution", Collections.emptyList());
            empty.put("summary", Collections.emptyMap());
            return Result.success(empty);
        }
        Long merchantId = (Long) request.getAttribute("userId");
        MerchantBehaviorContext context = buildMerchantBehaviorContext(merchantId, days);
        List<Map<String, Object>> behaviorStats = buildBehaviorStats(context);

        Map<String, Long> segmentCounts = new LinkedHashMap<>();
        Map<String, String> segmentNameMap = new LinkedHashMap<>();
        for (MerchantUserMetrics metrics : context.userMetrics.values()) {
            String segmentCode = StringUtils.hasText(metrics.segmentCode) ? metrics.segmentCode : "UNASSIGNED";
            String segmentName = StringUtils.hasText(metrics.segmentName) ? metrics.segmentName : "未分群";
            segmentCounts.merge(segmentCode, 1L, Long::sum);
            segmentNameMap.put(segmentCode, segmentName);
        }
        List<Map<String, Object>> segmentDistribution = new ArrayList<>();
        segmentCounts.forEach((segmentCode, userCount) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("segmentCode", segmentCode);
            item.put("segmentName", segmentNameMap.getOrDefault(segmentCode, "未分群"));
            item.put("userCount", userCount);
            segmentDistribution.add(item);
        });

        long totalBehaviorEvents = behaviorStats.stream()
                .map(item -> item.get("count"))
                .filter(Objects::nonNull)
                .mapToLong(value -> ((Number) value).longValue())
                .sum();
        BigDecimal totalSpend30d = context.userMetrics.values().stream()
                .map(metrics -> metrics.spend30d)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double avgScore = context.userMetrics.isEmpty() ? 0
                : context.userMetrics.values().stream().mapToInt(metrics -> metrics.score).average().orElse(0);

        int safeDays = Math.max(1, Math.min(days, 180));
        Map<String, Object> summary = buildBehaviorKpiSummary(context, safeDays);
        summary.put("totalBehaviorEvents", totalBehaviorEvents);
        summary.put("totalSpend30d", totalSpend30d.setScale(2, RoundingMode.HALF_UP));
        summary.put("avgScore", Math.round(avgScore * 10.0) / 10.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("behaviorStats", behaviorStats);
        result.put("segmentDistribution", segmentDistribution);
        result.put("summary", summary);
        return Result.success(result);
    }

    @GetMapping("/analytics/users")
    public Result<?> analyticsUsers(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(defaultValue = "30") int days,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String segmentCode,
                                    @RequestParam(required = false) Integer minScore,
                                    @RequestParam(required = false) BigDecimal minSpend30d,
                                    HttpServletRequest request) {
        if (page <= 0 || size <= 0) {
            throw new BusinessException("分页参数不合法");
        }
        if (!moduleSwitchService.isEnabled("recommendation")) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("records", Collections.emptyList());
            empty.put("total", 0);
            empty.put("current", page);
            empty.put("size", size);
            empty.put("summary", Collections.emptyMap());
            return Result.success(empty);
        }
        Long merchantId = (Long) request.getAttribute("userId");
        MerchantBehaviorContext context = buildMerchantBehaviorContext(merchantId, days);

        String keywordLower = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedSegmentCode = segmentCode == null ? "" : segmentCode.trim().toUpperCase(Locale.ROOT);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (MerchantUserMetrics metrics : context.userMetrics.values()) {
            if (!matchesKeyword(metrics, keywordLower)) {
                continue;
            }
            if (!matchesSegment(metrics, normalizedSegmentCode)) {
                continue;
            }
            if (minScore != null && metrics.score < minScore) {
                continue;
            }
            if (minSpend30d != null && metrics.spend30d.compareTo(minSpend30d) < 0) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", metrics.userId);
            row.put("username", metrics.user == null ? null : metrics.user.getUsername());
            row.put("nickname", metrics.user == null ? null : metrics.user.getNickname());
            row.put("phone", metrics.user == null ? null : metrics.user.getPhone());
            row.put("avatar", metrics.user == null ? null : metrics.user.getAvatar());
            row.put("segmentCode", StringUtils.hasText(metrics.segmentCode) ? metrics.segmentCode : "UNASSIGNED");
            row.put("segmentName", StringUtils.hasText(metrics.segmentName) ? metrics.segmentName : "未分群");
            row.put("segmentConfidence", metrics.segmentConfidence == null
                    ? BigDecimal.ZERO
                    : metrics.segmentConfidence.setScale(4, java.math.RoundingMode.HALF_UP));
            row.put("score", metrics.score);
            row.put("spend30d", metrics.spend30d.setScale(2, java.math.RoundingMode.HALF_UP));
            row.put("orderCount30d", metrics.orderIds30d.size());
            row.put("behaviorCount30d", metrics.behaviorCount);
            row.put("activeDays30d", metrics.activeDays30d.size());
            row.put("viewCount30d", metrics.viewCount);
            row.put("cartCount30d", metrics.cartCount);
            row.put("favoriteCount30d", metrics.favoriteCount);
            row.put("purchaseCount30d", metrics.purchaseCount);
            row.put("lastBehaviorTime", metrics.lastBehaviorTime);
            rows.add(row);
        }

        rows.sort((left, right) -> {
            int scoreCompare = Integer.compare(
                    ((Number) right.get("score")).intValue(),
                    ((Number) left.get("score")).intValue());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            BigDecimal rightSpend = (BigDecimal) right.get("spend30d");
            BigDecimal leftSpend = (BigDecimal) left.get("spend30d");
            int spendCompare = rightSpend.compareTo(leftSpend);
            if (spendCompare != 0) {
                return spendCompare;
            }
            LocalDateTime rightTime = (LocalDateTime) right.get("lastBehaviorTime");
            LocalDateTime leftTime = (LocalDateTime) left.get("lastBehaviorTime");
            if (leftTime == null && rightTime == null) {
                return 0;
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            return rightTime.compareTo(leftTime);
        });

        int fromIndex = Math.min((page - 1) * size, rows.size());
        int toIndex = Math.min(fromIndex + size, rows.size());
        List<Map<String, Object>> records = rows.subList(fromIndex, toIndex);

        BigDecimal totalSpend30d = rows.stream()
                .map(row -> (BigDecimal) row.get("spend30d"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalBehaviorCount = rows.stream()
                .map(row -> ((Number) row.get("behaviorCount30d")).longValue())
                .reduce(0L, Long::sum);
        double avgScore = rows.isEmpty() ? 0 : rows.stream()
                .mapToInt(row -> ((Number) row.get("score")).intValue())
                .average()
                .orElse(0);
        long viewUv = rows.stream()
                .filter(row -> ((Number) row.get("viewCount30d")).longValue() > 0)
                .count();
        long cartUv = rows.stream()
                .filter(row -> ((Number) row.get("cartCount30d")).longValue() > 0)
                .count();
        long purchaseUv = rows.stream()
                .filter(row -> ((Number) row.get("orderCount30d")).longValue() > 0)
                .count();
        long repeatPurchaseUv = rows.stream()
                .filter(row -> ((Number) row.get("orderCount30d")).longValue() >= 2)
                .count();
        BigDecimal addCartRate = viewUv == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(cartUv)
                .divide(BigDecimal.valueOf(viewUv), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal orderConversionRate = viewUv == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(purchaseUv)
                .divide(BigDecimal.valueOf(viewUv), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal repurchaseRate = purchaseUv == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(repeatPurchaseUv)
                .divide(BigDecimal.valueOf(purchaseUv), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("activeUsers", rows.size());
        summary.put("totalSpend30d", totalSpend30d.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalBehaviorCount30d", totalBehaviorCount);
        summary.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
        summary.put("viewUv", viewUv);
        summary.put("productViewUv", viewUv);
        summary.put("storeViewUv", viewUv);
        summary.put("cartUv", cartUv);
        summary.put("purchaseUv", purchaseUv);
        summary.put("repeatPurchaseUv", repeatPurchaseUv);
        summary.put("addToCartRate", addCartRate.setScale(2, RoundingMode.HALF_UP));
        summary.put("orderConversionRate", orderConversionRate.setScale(2, RoundingMode.HALF_UP));
        summary.put("repurchaseRate", repurchaseRate.setScale(2, RoundingMode.HALF_UP));
        summary.put("days", Math.max(1, Math.min(days, 180)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", rows.size());
        result.put("current", page);
        result.put("size", size);
        result.put("summary", summary);
        result.put("behaviorStats", buildBehaviorStats(context));
        return Result.success(result);
    }

    // ==================== 商家优惠券 ====================

    @GetMapping("/coupons")
    public Result<?> coupons(@RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "10") int size,
                             @RequestParam(required = false) Integer days,
                             @RequestParam(required = false) Integer status,
                             HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long merchantId = (Long) request.getAttribute("userId");

        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getScopeType, Constants.CouponScope.MERCHANT_STORE)
                .eq(Coupon::getMerchantId, merchantId)
                .orderByDesc(Coupon::getCreateTime);
        if (status != null) {
            wrapper.eq(Coupon::getStatus, status);
        }
        IPage<Coupon> couponPage = couponMapper.selectPage(new Page<>(page, size), wrapper);
        LocalDateTime startTime = null;
        if (days != null) {
            int safeDays = Math.max(7, Math.min(days, 180));
            startTime = LocalDateTime.now().minusDays(safeDays).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        enrichCouponConversionStats(couponPage, startTime);
        return Result.success(couponPage);
    }

    @GetMapping("/coupons/conversion-dashboard")
    public Result<?> couponConversionDashboard(@RequestParam(defaultValue = "30") int days,
                                               HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long merchantId = (Long) request.getAttribute("userId");
        int safeDays = Math.max(7, Math.min(days, 180));

        List<Coupon> merchantCoupons = couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getScopeType, Constants.CouponScope.MERCHANT_STORE)
                        .eq(Coupon::getMerchantId, merchantId)
                        .orderByDesc(Coupon::getCreateTime));
        LocalDateTime startTime = LocalDateTime.now()
                .minusDays(safeDays)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        CouponConversionSnapshot snapshot = buildCouponConversionSnapshot(merchantCoupons, startTime, safeDays);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", snapshot.summary);
        result.put("trend", snapshot.trend);
        result.put("couponBreakdown", snapshot.breakdown);
        result.put("days", safeDays);
        return Result.success(result);
    }

    @PostMapping("/coupons")
    @Log(module = "商家优惠券", action = "创建优惠券")
    public Result<?> createCoupon(@RequestBody Coupon coupon, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long merchantId = (Long) request.getAttribute("userId");

        Coupon preparedCoupon = prepareMerchantCouponForSave(coupon, merchantId);
        validateMerchantCouponPayload(preparedCoupon);
        couponMapper.insert(preparedCoupon);
        return Result.success("商家优惠券创建成功", preparedCoupon);
    }

    @PostMapping("/coupons/{couponId}/issue")
    @Transactional(rollbackFor = Exception.class)
    @Log(module = "商家优惠券", action = "定向发放")
    public Result<?> issueCoupon(@PathVariable Long couponId,
                                 @RequestBody Map<String, Object> payload,
                                 HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long merchantId = (Long) request.getAttribute("userId");
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null
                || !Objects.equals(coupon.getMerchantId(), merchantId)
                || !Objects.equals(coupon.getScopeType(), Constants.CouponScope.MERCHANT_STORE)) {
            throw new BusinessException("优惠券不存在或无权操作");
        }

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStatus() == null
                || coupon.getStatus() != Constants.CouponStatus.ACTIVE
                || coupon.getStartTime() == null
                || coupon.getEndTime() == null
                || now.isBefore(coupon.getStartTime())
                || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException("优惠券不在可发放时间范围内");
        }

        Set<Long> requestedUserIds = parseUserIds(payload == null ? null : payload.get("userIds"));
        if (requestedUserIds.isEmpty()) {
            throw new BusinessException("请至少选择一个目标用户");
        }
        Map<String, Object> result = issueCouponToUsers(coupon, requestedUserIds);
        return Result.success("发放成功", result);
    }

    @PostMapping("/coupons/{couponId}/issue-by-filter")
    @Transactional(rollbackFor = Exception.class)
    @Log(module = "商家优惠券", action = "按筛选条件一键发放")
    public Result<?> issueCouponByFilter(@PathVariable Long couponId,
                                         @RequestBody(required = false) Map<String, Object> payload,
                                         HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long merchantId = (Long) request.getAttribute("userId");
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null
                || !Objects.equals(coupon.getMerchantId(), merchantId)
                || !Objects.equals(coupon.getScopeType(), Constants.CouponScope.MERCHANT_STORE)) {
            throw new BusinessException("优惠券不存在或无权操作");
        }

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStatus() == null
                || coupon.getStatus() != Constants.CouponStatus.ACTIVE
                || coupon.getStartTime() == null
                || coupon.getEndTime() == null
                || now.isBefore(coupon.getStartTime())
                || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException("优惠券不在可发放时间范围内");
        }

        int days = resolveInt(payload == null ? null : payload.get("days"), 30);
        int safeDays = Math.max(1, Math.min(days, 180));
        Integer minScore = resolveNullableInt(payload == null ? null : payload.get("minScore"));
        BigDecimal minSpend30d = resolveNullableBigDecimal(payload == null ? null : payload.get("minSpend30d"));
        int maxIssueCount = resolveInt(payload == null ? null : payload.get("maxIssueCount"), 500);
        int safeMaxIssueCount = Math.max(1, Math.min(maxIssueCount, 2000));
        String keyword = payload == null ? null : safeTrim(String.valueOf(payload.getOrDefault("keyword", "")));
        String segmentCode = payload == null ? null : safeTrim(String.valueOf(payload.getOrDefault("segmentCode", "")));

        MerchantBehaviorContext context = buildMerchantBehaviorContext(merchantId, safeDays);
        String keywordLower = StringUtils.hasText(keyword) ? keyword.toLowerCase(Locale.ROOT) : "";
        String normalizedSegmentCode = StringUtils.hasText(segmentCode)
                ? segmentCode.toUpperCase(Locale.ROOT)
                : "";

        List<MerchantUserMetrics> matched = context.userMetrics.values().stream()
                .filter(metrics -> matchesKeyword(metrics, keywordLower))
                .filter(metrics -> matchesSegment(metrics, normalizedSegmentCode))
                .filter(metrics -> minScore == null || metrics.score >= minScore)
                .filter(metrics -> minSpend30d == null || metrics.spend30d.compareTo(minSpend30d) >= 0)
                .sorted((left, right) -> {
                    int scoreCompare = Integer.compare(right.score, left.score);
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    int spendCompare = right.spend30d.compareTo(left.spend30d);
                    if (spendCompare != 0) {
                        return spendCompare;
                    }
                    if (left.lastBehaviorTime == null && right.lastBehaviorTime == null) {
                        return 0;
                    }
                    if (left.lastBehaviorTime == null) {
                        return 1;
                    }
                    if (right.lastBehaviorTime == null) {
                        return -1;
                    }
                    return right.lastBehaviorTime.compareTo(left.lastBehaviorTime);
                })
                .collect(Collectors.toList());

        if (matched.isEmpty()) {
            throw new BusinessException("当前筛选条件下没有可发放用户");
        }

        LinkedHashSet<Long> targetUserIds = matched.stream()
                .map(metrics -> metrics.userId)
                .filter(Objects::nonNull)
                .limit(safeMaxIssueCount)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (targetUserIds.isEmpty()) {
            throw new BusinessException("当前筛选条件下没有可发放用户");
        }

        Map<String, Object> result = issueCouponToUsers(coupon, targetUserIds);
        result.put("matchedCount", matched.size());
        result.put("targetCount", targetUserIds.size());
        result.put("days", safeDays);
        result.put("minScore", minScore);
        result.put("minSpend30d", minSpend30d == null ? null : minSpend30d.setScale(2, RoundingMode.HALF_UP));
        result.put("segmentCode", normalizedSegmentCode);
        return Result.success("发放成功", result);
    }

    @GetMapping("/products")
    public Result<?> products(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        return Result.success(productService.getMerchantProducts(merchantId, page, size,
                keyword, status, categoryId, stockStatus, sortField, sortOrder));
    }

    @Log(module = "商家商品", action = "创建商品")
    @PostMapping("/products")
    public Result<?> createProduct(@RequestBody Product product, HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        product.setMerchantId(merchantId);
        product.setStatus(Constants.ProductStatus.ON_SHELF);
        product.setSalesCount(0);
        product.setRating(new BigDecimal("5.0"));
        productService.save(product);
        return Result.success("商品创建成功", product);
    }

    @PutMapping("/products/{id}")
    public Result<?> updateProduct(@PathVariable Long id, @RequestBody Product product,
                                    HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        Product existing = productService.getById(id);
        if (existing == null || !Objects.equals(existing.getMerchantId(), merchantId)) {
            throw new BusinessException("商品不存在或无权操作");
        }
        productService.updateById(buildSafeProductUpdate(id, product));
        return Result.success("商品更新成功");
    }

    @Log(module = "商家商品", action = "删除商品")
    @DeleteMapping("/products/{id}")
    public Result<?> deleteProduct(@PathVariable Long id, HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        Product existing = productService.getById(id);
        if (existing == null || !Objects.equals(existing.getMerchantId(), merchantId)) {
            throw new BusinessException("商品不存在或无权操作");
        }
        productService.removeById(id);
        return Result.success("商品删除成功");
    }

    @PutMapping("/products/{id}/status")
    public Result<?> toggleProductStatus(@PathVariable Long id, @RequestBody Map<String, Integer> params,
                                          HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        Product existing = productService.getById(id);
        if (existing == null || !Objects.equals(existing.getMerchantId(), merchantId)) {
            throw new BusinessException("商品不存在或无权操作");
        }
        Integer status = params.get("status");
        if (status == null) {
            throw new BusinessException("请指定商品状态");
        }
        Product update = new Product();
        update.setId(id);
        update.setStatus(status);
        productService.updateById(update);
        return Result.success("状态更新成功");
    }

    @Log(module = "商家商品", action = "批量更新商品状态")
    @PutMapping("/products/batch/status")
    public Result<?> batchUpdateProductStatus(@Validated @RequestBody MerchantProductBatchStatusDTO dto,
                                              HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        int affected = productService.batchUpdateMerchantProductStatus(
                merchantId, dto.getProductIds(), dto.getStatus());
        return Result.success("批量状态更新成功", Collections.singletonMap("affected", affected));
    }

    @Log(module = "商家商品", action = "批量更新商品库存")
    @PutMapping("/products/batch/stock")
    public Result<?> batchUpdateProductStock(@Validated @RequestBody MerchantProductBatchStockDTO dto,
                                             HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        int affected = productService.batchUpdateMerchantProductStock(
                merchantId, dto.getProductIds(), dto.getOperation(), dto.getStock());
        return Result.success("批量库存更新成功", Collections.singletonMap("affected", affected));
    }

    @GetMapping("/products/low-stock")
    public Result<?> lowStockProducts(@RequestParam(defaultValue = "50") int threshold,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      HttpServletRequest request) {
        if (threshold < 0) {
            throw new BusinessException("库存预警阈值不能小于0");
        }
        Long merchantId = (Long) request.getAttribute("userId");
        return Result.success(productService.getMerchantLowStockProducts(merchantId, threshold, page, size));
    }

    @GetMapping("/products/stats")
    public Result<?> productStats(@RequestParam(defaultValue = "50") int lowStockThreshold,
                                  @RequestParam(defaultValue = "5") int topLimit,
                                  HttpServletRequest request) {
        if (lowStockThreshold < 0) {
            throw new BusinessException("库存预警阈值不能小于0");
        }
        if (topLimit <= 0) {
            throw new BusinessException("热销商品数量必须大于0");
        }
        Long merchantId = (Long) request.getAttribute("userId");
        return Result.success(productService.getMerchantProductStats(merchantId, lowStockThreshold, topLimit));
    }

    @GetMapping("/orders")
    public Result<?> orders(
            @RequestParam(defaultValue = "-1") Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        int safePage = page <= 0 ? 1 : page;
        int safeSize = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        return Result.success(orderService.getMerchantOrders(merchantId, status == -1 ? null : status, safePage, safeSize));
    }

    @Log(module = "商家订单", action = "发货")
    @PostMapping("/orders/{id}/ship")
    public Result<?> shipOrder(@PathVariable Long id, HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");

        Order order = orderService.getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != Constants.OrderStatus.PAID) {
            throw new BusinessException("只有已付款的订单才能发货");
        }
        orderService.checkMerchantOwnsOrder(id, merchantId);

        orderService.updateOrderStatus(id, Constants.OrderStatus.SHIPPED);
        return Result.success("发货成功");
    }

    // ==================== 评价回复 ====================

    @GetMapping("/reviews")
    public Result<?> reviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        List<Product> myProducts = productService.list(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchantId)
                        .select(Product::getId));
        if (myProducts.isEmpty()) {
            return Result.success(new Page<>(page, size));
        }
        Set<Long> pids = myProducts.stream().map(Product::getId).collect(Collectors.toSet());
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ProductReview::getProductId, pids)
               .orderByDesc(ProductReview::getCreateTime);
        return Result.success(reviewMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @PostMapping("/reviews/{id}/reply")
    public Result<?> replyReview(@PathVariable Long id, @RequestBody Map<String, String> params,
                                  HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        String replyContent = params.get("reply");
        if (replyContent == null || replyContent.isEmpty()) {
            throw new BusinessException("回复内容不能为空");
        }
        ProductReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        Product product = productService.getById(review.getProductId());
        if (product == null || !java.util.Objects.equals(product.getMerchantId(), merchantId)) {
            throw new BusinessException(403, "只能回复自己商品的评价");
        }
        review.setReply(replyContent);
        review.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(review);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "review");
        payload.put("reviewId", review.getId());
        payload.put("productId", review.getProductId());
        payload.put("status", "replied");
        managementWorkbenchRealtimeService.notifyMerchant(merchantId, "review-updated", payload);

        return Result.success("回复成功");
    }

    // ==================== 退款处理 ====================

    @GetMapping("/refunds")
    public Result<?> refunds(
            @RequestParam(defaultValue = "-1") Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long merchantId = (Long) request.getAttribute("userId");
        List<Product> myProducts = productService.list(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchantId)
                        .select(Product::getId));
        if (myProducts.isEmpty()) {
            return Result.success(new Page<>(page, size));
        }

        Set<Long> pids = myProducts.stream().map(Product::getId).collect(Collectors.toSet());
        List<OrderItem> ois = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getProductId, pids));
        Set<Long> orderIds = ois.stream().map(OrderItem::getOrderId).collect(Collectors.toSet());
        Set<Long> accessibleOrderIds = filterExclusiveMerchantOrderIds(pids, orderIds);
        if (accessibleOrderIds.isEmpty()) {
            return Result.success(new Page<>(page, size));
        }

        LambdaQueryWrapper<RefundRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(RefundRequest::getOrderId, accessibleOrderIds);
        if (status != null && status >= 0) {
            wrapper.eq(RefundRequest::getStatus, status);
        }
        wrapper.orderByDesc(RefundRequest::getCreateTime);
        IPage<RefundRequest> refundPage = refundMapper.selectPage(new Page<>(page, size), wrapper);
        refundPage.getRecords().forEach(RefundViewUtil::enrichRefundView);
        return Result.success(refundPage);
    }

    private MerchantBehaviorContext buildMerchantBehaviorContext(Long merchantId, int rawDays) {
        int days = Math.max(1, Math.min(rawDays, 180));
        LocalDateTime startTime = LocalDateTime.now().minusDays(days).withHour(0).withMinute(0).withSecond(0).withNano(0);

        MerchantBehaviorContext context = new MerchantBehaviorContext();
        List<Product> merchantProducts = productService.list(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchantId)
                        .select(Product::getId));
        Set<Long> productIds = merchantProducts.stream().map(Product::getId).collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return context;
        }

        List<UserBehavior> behaviors = userBehaviorMapper.selectList(
                new LambdaQueryWrapper<UserBehavior>()
                        .in(UserBehavior::getProductId, productIds)
                        .ge(UserBehavior::getCreateTime, startTime)
                        .select(UserBehavior::getUserId, UserBehavior::getBehaviorType, UserBehavior::getCreateTime));
        for (UserBehavior behavior : behaviors) {
            if (behavior == null || behavior.getUserId() == null) {
                continue;
            }
            MerchantUserMetrics metrics = context.userMetrics.computeIfAbsent(behavior.getUserId(), MerchantUserMetrics::new);
            metrics.behaviorCount += 1;
            if (behavior.getCreateTime() != null) {
                metrics.activeDays30d.add(behavior.getCreateTime().toLocalDate().toString());
                if (metrics.lastBehaviorTime == null || behavior.getCreateTime().isAfter(metrics.lastBehaviorTime)) {
                    metrics.lastBehaviorTime = behavior.getCreateTime();
                }
            }
            String behaviorType = behavior.getBehaviorType();
            if (Constants.BehaviorType.VIEW.equals(behaviorType)) {
                metrics.viewCount += 1;
            } else if (Constants.BehaviorType.CART.equals(behaviorType)) {
                metrics.cartCount += 1;
            } else if (Constants.BehaviorType.FAVORITE.equals(behaviorType)) {
                metrics.favoriteCount += 1;
            } else if (Constants.BehaviorType.PURCHASE.equals(behaviorType)) {
                metrics.purchaseCount += 1;
            }

            if (StringUtils.hasText(behaviorType)) {
                context.behaviorCount.merge(behaviorType, 1L, Long::sum);
                context.behaviorUsers.computeIfAbsent(behaviorType, key -> new HashSet<>()).add(behavior.getUserId());
            }
        }

        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getProductId, productIds)
                        .select(OrderItem::getOrderId, OrderItem::getSubtotal, OrderItem::getPrice, OrderItem::getQuantity));
        if (!orderItems.isEmpty()) {
            Set<Long> orderIds = orderItems.stream()
                    .map(OrderItem::getOrderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!orderIds.isEmpty()) {
                List<Order> orders = orderMapper.selectList(
                        new LambdaQueryWrapper<Order>()
                                .in(Order::getId, orderIds)
                                .ge(Order::getCreateTime, startTime)
                                .in(Order::getStatus, Arrays.asList(
                                        Constants.OrderStatus.PAID,
                                        Constants.OrderStatus.SHIPPED,
                                        Constants.OrderStatus.COMPLETED))
                                .select(Order::getId, Order::getUserId, Order::getCreateTime));
                Map<Long, Order> orderMap = orders.stream()
                        .collect(Collectors.toMap(Order::getId, order -> order, (left, right) -> left));

                for (OrderItem item : orderItems) {
                    if (item == null || item.getOrderId() == null) {
                        continue;
                    }
                    Order order = orderMap.get(item.getOrderId());
                    if (order == null || order.getUserId() == null) {
                        continue;
                    }
                    MerchantUserMetrics metrics = context.userMetrics.computeIfAbsent(order.getUserId(), MerchantUserMetrics::new);
                    metrics.spend30d = metrics.spend30d.add(resolveItemAmount(item));
                    metrics.orderIds30d.add(order.getId());
                }
            }
        }

        if (context.userMetrics.isEmpty()) {
            return context;
        }

        List<User> users = userMapper.selectBatchIds(new ArrayList<>(context.userMetrics.keySet()));
        Map<Long, User> userMap = users.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        for (MerchantUserMetrics metrics : context.userMetrics.values()) {
            metrics.user = userMap.get(metrics.userId);
        }

        AnalyticsKmeansTask latestTask = analyticsKmeansTaskMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsKmeansTask>()
                        .eq(AnalyticsKmeansTask::getStatus, "success")
                        .orderByDesc(AnalyticsKmeansTask::getSnapshotDate)
                        .orderByDesc(AnalyticsKmeansTask::getId)
                        .last("LIMIT 1"));
        if (latestTask != null && latestTask.getId() != null) {
            List<AnalyticsKmeansUserResult> results = analyticsKmeansUserResultMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsKmeansUserResult>()
                            .eq(AnalyticsKmeansUserResult::getTaskId, latestTask.getId())
                            .in(AnalyticsKmeansUserResult::getUserId, context.userMetrics.keySet())
                            .select(AnalyticsKmeansUserResult::getUserId,
                                    AnalyticsKmeansUserResult::getSegmentCode,
                                    AnalyticsKmeansUserResult::getSegmentName,
                                    AnalyticsKmeansUserResult::getConfidenceScore));
            Map<Long, AnalyticsKmeansUserResult> resultMap = results.stream()
                    .collect(Collectors.toMap(AnalyticsKmeansUserResult::getUserId, value -> value, (left, right) -> left));
            for (MerchantUserMetrics metrics : context.userMetrics.values()) {
                AnalyticsKmeansUserResult result = resultMap.get(metrics.userId);
                if (result == null) {
                    continue;
                }
                metrics.segmentCode = result.getSegmentCode();
                metrics.segmentName = result.getSegmentName();
                metrics.segmentConfidence = result.getConfidenceScore();
            }
        }

        for (MerchantUserMetrics metrics : context.userMetrics.values()) {
            metrics.score = calculateMerchantUserScore(metrics);
        }
        return context;
    }

    private List<Map<String, Object>> buildBehaviorStats(MerchantBehaviorContext context) {
        String[] orderedTypes = {
                Constants.BehaviorType.VIEW,
                Constants.BehaviorType.CART,
                Constants.BehaviorType.FAVORITE,
                Constants.BehaviorType.PURCHASE,
                Constants.BehaviorType.SEARCH
        };
        List<Map<String, Object>> stats = new ArrayList<>();
        for (String behaviorType : orderedTypes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("behaviorType", behaviorType);
            item.put("count", context.behaviorCount.getOrDefault(behaviorType, 0L));
            item.put("userCount", context.behaviorUsers.getOrDefault(behaviorType, Collections.emptySet()).size());
            stats.add(item);
        }
        return stats;
    }

    private boolean matchesKeyword(MerchantUserMetrics metrics, String keywordLower) {
        if (!StringUtils.hasText(keywordLower)) {
            return true;
        }
        if (metrics == null) {
            return false;
        }
        if (metrics.userId != null && String.valueOf(metrics.userId).contains(keywordLower)) {
            return true;
        }
        if (metrics.user == null) {
            return false;
        }
        return containsIgnoreCase(metrics.user.getUsername(), keywordLower)
                || containsIgnoreCase(metrics.user.getNickname(), keywordLower)
                || containsIgnoreCase(metrics.user.getPhone(), keywordLower);
    }

    private boolean matchesSegment(MerchantUserMetrics metrics, String normalizedSegmentCode) {
        if (!StringUtils.hasText(normalizedSegmentCode)) {
            return true;
        }
        String candidate = metrics == null ? null : metrics.segmentCode;
        return StringUtils.hasText(candidate)
                && normalizedSegmentCode.equals(candidate.trim().toUpperCase(Locale.ROOT));
    }

    private boolean containsIgnoreCase(String source, String keywordLower) {
        return StringUtils.hasText(source)
                && source.toLowerCase(Locale.ROOT).contains(keywordLower);
    }

    private int calculateMerchantUserScore(MerchantUserMetrics metrics) {
        if (metrics == null) {
            return 0;
        }
        double score = 0;
        score += metrics.viewCount;
        score += metrics.cartCount * 2.0;
        score += metrics.favoriteCount * 3.0;
        score += metrics.purchaseCount * 8.0;
        score += metrics.orderIds30d.size() * 8.0;
        score += metrics.activeDays30d.size() * 2.0;
        score += metrics.spend30d.doubleValue() / 120.0;
        if (metrics.segmentConfidence != null) {
            score += metrics.segmentConfidence.doubleValue() * 8.0;
        }
        int rounded = (int) Math.round(score);
        if (rounded < 0) {
            return 0;
        }
        return Math.min(rounded, 100);
    }

    private Coupon prepareMerchantCouponForSave(Coupon source, Long merchantId) {
        Coupon coupon = new Coupon();
        coupon.setName(source == null ? null : safeTrim(source.getName()));
        coupon.setType(source == null ? null : source.getType());
        coupon.setValue(source == null ? null : source.getValue());
        coupon.setMinAmount(source == null || source.getMinAmount() == null ? BigDecimal.ZERO : source.getMinAmount());
        coupon.setMaxDiscount(source == null ? null : source.getMaxDiscount());
        coupon.setTotalCount(source == null ? null : source.getTotalCount());
        coupon.setUsedCount(source == null || source.getUsedCount() == null ? 0 : source.getUsedCount());
        coupon.setStartTime(source == null ? null : source.getStartTime());
        coupon.setEndTime(source == null ? null : source.getEndTime());
        coupon.setStatus(source == null || source.getStatus() == null ? Constants.CouponStatus.ACTIVE : source.getStatus());
        coupon.setScopeType(Constants.CouponScope.MERCHANT_STORE);
        coupon.setMerchantId(merchantId);
        coupon.setAudienceType(couponAudienceService.normalizeAudienceType(source == null ? null : source.getAudienceType()));

        String audienceNote = source == null ? null : source.getAudienceNote();
        coupon.setAudienceNote(audienceNote == null ? "" : audienceNote.trim());

        if (coupon.getType() == null || coupon.getType() != Constants.CouponType.DISCOUNT) {
            coupon.setMaxDiscount(null);
        }
        if (coupon.getAudienceType() == CouponAudienceService.AUDIENCE_SEGMENT) {
            coupon.setTargetSegmentCodes(couponAudienceService.normalizeSegmentCodeList(source == null ? null : source.getTargetSegmentCodes()));
            coupon.setTargetUserIds("");
        } else if (coupon.getAudienceType() == CouponAudienceService.AUDIENCE_USER) {
            coupon.setTargetUserIds(couponAudienceService.normalizeTargetUserIdList(source == null ? null : source.getTargetUserIds()));
            coupon.setTargetSegmentCodes("");
        } else {
            coupon.setTargetSegmentCodes("");
            coupon.setTargetUserIds("");
        }
        return coupon;
    }

    private void validateMerchantCouponPayload(Coupon coupon) {
        if (coupon == null) {
            throw new BusinessException("优惠券参数不能为空");
        }
        if (!StringUtils.hasText(coupon.getName())) {
            throw new BusinessException("优惠券名称不能为空");
        }
        if (coupon.getType() == null
                || (coupon.getType() != Constants.CouponType.FULL_REDUCTION
                && coupon.getType() != Constants.CouponType.DISCOUNT
                && coupon.getType() != Constants.CouponType.NO_THRESHOLD)) {
            throw new BusinessException("优惠券类型不合法");
        }
        if (coupon.getValue() == null || coupon.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("优惠券面额或折扣值必须大于0");
        }
        if (coupon.getType() == Constants.CouponType.DISCOUNT
                && coupon.getValue().compareTo(new BigDecimal("10")) >= 0) {
            throw new BusinessException("折扣券折扣值必须小于10");
        }
        if (coupon.getMinAmount() == null || coupon.getMinAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("优惠券门槛金额不能小于0");
        }
        if (coupon.getType() == Constants.CouponType.DISCOUNT
                && coupon.getMaxDiscount() != null
                && coupon.getMaxDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("折扣券最高优惠金额必须大于0");
        }
        if (coupon.getTotalCount() == null || coupon.getTotalCount() <= 0) {
            throw new BusinessException("优惠券库存必须大于0");
        }
        if (coupon.getUsedCount() == null || coupon.getUsedCount() < 0 || coupon.getUsedCount() > coupon.getTotalCount()) {
            throw new BusinessException("已发放数量不合法");
        }
        if (coupon.getStartTime() == null || coupon.getEndTime() == null || !coupon.getEndTime().isAfter(coupon.getStartTime())) {
            throw new BusinessException("优惠券有效期配置不合法");
        }
        if (coupon.getStatus() == null
                || (coupon.getStatus() != Constants.CouponStatus.NOT_STARTED
                && coupon.getStatus() != Constants.CouponStatus.ACTIVE
                && coupon.getStatus() != Constants.CouponStatus.ENDED)) {
            throw new BusinessException("优惠券状态不合法");
        }
        if (coupon.getAudienceType() == CouponAudienceService.AUDIENCE_SEGMENT
                && couponAudienceService.parseSegmentCodes(coupon.getTargetSegmentCodes()).isEmpty()) {
            throw new BusinessException("分群定向券至少需要配置一个分群编码");
        }
        if (coupon.getAudienceType() == CouponAudienceService.AUDIENCE_USER
                && couponAudienceService.parseTargetUserIds(coupon.getTargetUserIds()).isEmpty()) {
            throw new BusinessException("指定用户券至少需要配置一个用户ID");
        }
    }

    private Map<String, Object> issueCouponToUsers(Coupon coupon, Set<Long> requestedUserIds) {
        if (coupon == null || coupon.getId() == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (requestedUserIds == null || requestedUserIds.isEmpty()) {
            throw new BusinessException("请至少选择一个目标用户");
        }

        List<User> candidates = userMapper.selectBatchIds(new ArrayList<>(requestedUserIds));
        Set<Long> validUserIds = candidates.stream()
                .filter(user -> user != null
                        && Objects.equals(user.getStatus(), 1)
                        && !Constants.Role.ADMIN.equalsIgnoreCase(user.getRole())
                        && !Constants.Role.MERCHANT.equalsIgnoreCase(user.getRole()))
                .map(User::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (validUserIds.isEmpty()) {
            throw new BusinessException("没有可发放的有效用户");
        }

        List<UserCoupon> existingCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getCouponId, coupon.getId())
                        .in(UserCoupon::getUserId, validUserIds)
                        .select(UserCoupon::getUserId));
        Set<Long> existingUserIds = existingCoupons.stream()
                .map(UserCoupon::getUserId)
                .collect(Collectors.toSet());

        List<Long> toIssueUserIds = validUserIds.stream()
                .filter(userId -> !existingUserIds.contains(userId))
                .collect(Collectors.toList());
        if (toIssueUserIds.isEmpty()) {
            throw new BusinessException("目标用户均已持有该优惠券");
        }

        int availableCount = (coupon.getTotalCount() == null ? 0 : coupon.getTotalCount())
                - (coupon.getUsedCount() == null ? 0 : coupon.getUsedCount());
        if (availableCount < toIssueUserIds.size()) {
            throw new BusinessException("优惠券库存不足，剩余可发放 " + Math.max(availableCount, 0) + " 张");
        }

        for (Long userId : toIssueUserIds) {
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setUserId(userId);
            userCoupon.setCouponId(coupon.getId());
            userCoupon.setStatus(0);
            userCouponMapper.insert(userCoupon);
        }
        int affected = couponMapper.incrementUsedCountByAmount(coupon.getId(), toIssueUserIds.size());
        if (affected == 0) {
            throw new BusinessException("优惠券库存更新失败，请稍后重试");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issuedCount", toIssueUserIds.size());
        result.put("skippedCount", existingUserIds.size());
        result.put("invalidCount", requestedUserIds.size() - validUserIds.size());
        result.put("couponId", coupon.getId());
        return result;
    }

    private Map<String, Object> buildBehaviorKpiSummary(MerchantBehaviorContext context, int days) {
        Map<String, Object> summary = new LinkedHashMap<>();
        long activeUsers = context == null ? 0 : context.userMetrics.size();
        long viewUv = context == null ? 0 : context.userMetrics.values().stream()
                .filter(metrics -> metrics.viewCount > 0)
                .count();
        long cartUv = context == null ? 0 : context.userMetrics.values().stream()
                .filter(metrics -> metrics.cartCount > 0)
                .count();
        long purchaseUv = context == null ? 0 : context.userMetrics.values().stream()
                .filter(metrics -> !metrics.orderIds30d.isEmpty() || metrics.purchaseCount > 0)
                .count();
        long repeatPurchaseUv = context == null ? 0 : context.userMetrics.values().stream()
                .filter(metrics -> metrics.orderIds30d.size() >= 2)
                .count();

        BigDecimal addToCartRate = viewUv == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(cartUv)
                .divide(BigDecimal.valueOf(viewUv), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal orderConversionRate = viewUv == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(purchaseUv)
                .divide(BigDecimal.valueOf(viewUv), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        BigDecimal repurchaseRate = purchaseUv == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(repeatPurchaseUv)
                .divide(BigDecimal.valueOf(purchaseUv), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        summary.put("activeUsers", activeUsers);
        summary.put("viewUv", viewUv);
        summary.put("productViewUv", viewUv);
        summary.put("storeViewUv", viewUv);
        summary.put("cartUv", cartUv);
        summary.put("purchaseUv", purchaseUv);
        summary.put("repeatPurchaseUv", repeatPurchaseUv);
        summary.put("addToCartRate", addToCartRate.setScale(2, RoundingMode.HALF_UP));
        summary.put("orderConversionRate", orderConversionRate.setScale(2, RoundingMode.HALF_UP));
        summary.put("repurchaseRate", repurchaseRate.setScale(2, RoundingMode.HALF_UP));
        summary.put("days", Math.max(1, Math.min(days, 180)));
        return summary;
    }

    private void enrichCouponConversionStats(IPage<Coupon> couponPage, LocalDateTime issueStartTime) {
        if (couponPage == null || couponPage.getRecords() == null || couponPage.getRecords().isEmpty()) {
            return;
        }
        CouponConversionSnapshot snapshot = buildCouponConversionSnapshot(couponPage.getRecords(), issueStartTime, 0);
        Map<Long, Map<String, Object>> breakdownMap = snapshot.breakdown.stream()
                .filter(item -> item.get("couponId") != null)
                .collect(Collectors.toMap(
                        item -> ((Number) item.get("couponId")).longValue(),
                        item -> item,
                        (left, right) -> left));

        for (Coupon coupon : couponPage.getRecords()) {
            Map<String, Object> item = breakdownMap.get(coupon.getId());
            if (item == null) {
                coupon.setIssuedCount(0);
                coupon.setRedeemedCount(0);
                coupon.setPaidOrderCount(0);
                coupon.setRedeemRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                coupon.setPaidOrderRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                coupon.setPaidGmv(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                coupon.setAvgOrderAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                continue;
            }
            coupon.setIssuedCount(((Number) item.getOrDefault("issuedCount", 0)).intValue());
            coupon.setRedeemedCount(((Number) item.getOrDefault("redeemedCount", 0)).intValue());
            coupon.setPaidOrderCount(((Number) item.getOrDefault("paidOrderCount", 0)).intValue());
            coupon.setRedeemRate((BigDecimal) item.getOrDefault("redeemRate", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
            coupon.setPaidOrderRate((BigDecimal) item.getOrDefault("paidOrderRate", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
            coupon.setPaidGmv((BigDecimal) item.getOrDefault("paidGmv", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
            coupon.setAvgOrderAmount((BigDecimal) item.getOrDefault("avgOrderAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
        }
    }

    private CouponConversionSnapshot buildCouponConversionSnapshot(List<Coupon> coupons,
                                                                  LocalDateTime issueStartTime,
                                                                  int trendDays) {
        CouponConversionSnapshot snapshot = new CouponConversionSnapshot();
        if (coupons == null || coupons.isEmpty()) {
            snapshot.summary = buildEmptyCouponDashboardSummary(Math.max(0, trendDays));
            snapshot.trend = buildEmptyCouponTrend(Math.max(0, trendDays));
            snapshot.breakdown = Collections.emptyList();
            return snapshot;
        }

        List<Long> couponIds = coupons.stream()
                .map(Coupon::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (couponIds.isEmpty()) {
            snapshot.summary = buildEmptyCouponDashboardSummary(Math.max(0, trendDays));
            snapshot.trend = buildEmptyCouponTrend(Math.max(0, trendDays));
            snapshot.breakdown = Collections.emptyList();
            return snapshot;
        }

        LambdaQueryWrapper<UserCoupon> userCouponWrapper = new LambdaQueryWrapper<UserCoupon>()
                .in(UserCoupon::getCouponId, couponIds)
                .select(UserCoupon::getId, UserCoupon::getCouponId, UserCoupon::getStatus,
                        UserCoupon::getCreateTime, UserCoupon::getUseTime);
        if (issueStartTime != null) {
            userCouponWrapper.ge(UserCoupon::getCreateTime, issueStartTime);
        }
        List<UserCoupon> cohortUserCoupons = userCouponMapper.selectList(userCouponWrapper);
        Map<Long, List<UserCoupon>> userCouponMap = cohortUserCoupons.stream()
                .filter(item -> item.getCouponId() != null)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId));

        Set<Long> cohortUserCouponIds = cohortUserCoupons.stream()
                .map(UserCoupon::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Long> userCouponIdToCouponId = cohortUserCoupons.stream()
                .filter(userCoupon -> userCoupon.getId() != null && userCoupon.getCouponId() != null)
                .collect(Collectors.toMap(UserCoupon::getId, UserCoupon::getCouponId, (left, right) -> left));

        Map<Long, List<Order>> paidOrdersByCouponId = new HashMap<>();
        if (!cohortUserCouponIds.isEmpty()) {
            List<Order> paidOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .in(Order::getUserCouponId, cohortUserCouponIds)
                            .in(Order::getStatus, Arrays.asList(
                                    Constants.OrderStatus.PAID,
                                    Constants.OrderStatus.SHIPPED,
                                    Constants.OrderStatus.COMPLETED))
                            .select(Order::getId, Order::getUserCouponId, Order::getTotalAmount,
                                    Order::getCreateTime, Order::getPayTime));
            for (Order order : paidOrders) {
                if (order == null || order.getUserCouponId() == null) {
                    continue;
                }
                Long couponId = userCouponIdToCouponId.get(order.getUserCouponId());
                if (couponId == null) {
                    continue;
                }
                paidOrdersByCouponId.computeIfAbsent(couponId, key -> new ArrayList<>()).add(order);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("couponCount", coupons.size());
        summary.put("activeCouponCount", coupons.stream()
                .filter(coupon -> Objects.equals(coupon.getStatus(), Constants.CouponStatus.ACTIVE))
                .count());
        summary.put("issuedCount", 0);
        summary.put("redeemedCount", 0);
        summary.put("paidOrderCount", 0);
        summary.put("paidGmv", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        summary.put("avgOrderAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        summary.put("redeemRate", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        summary.put("paidOrderRate", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        summary.put("days", Math.max(0, trendDays));

        List<Map<String, Object>> breakdown = new ArrayList<>();
        int totalIssued = 0;
        int totalRedeemed = 0;
        int totalPaidOrders = 0;
        BigDecimal totalPaidGmv = BigDecimal.ZERO;

        for (Coupon coupon : coupons) {
            List<UserCoupon> issuedCoupons = userCouponMap.getOrDefault(coupon.getId(), Collections.emptyList());
            List<Order> paidOrders = paidOrdersByCouponId.getOrDefault(coupon.getId(), Collections.emptyList());

            int issuedCount = issuedCoupons.size();
            int redeemedCount = (int) issuedCoupons.stream()
                    .filter(userCoupon -> Objects.equals(userCoupon.getStatus(), 1))
                    .count();
            int paidOrderCount = paidOrders.size();
            BigDecimal paidGmv = paidOrders.stream()
                    .map(order -> order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal avgOrderAmount = paidOrderCount == 0
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : paidGmv.divide(BigDecimal.valueOf(paidOrderCount), 2, RoundingMode.HALF_UP);
            BigDecimal redeemRate = issuedCount == 0
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(redeemedCount)
                    .divide(BigDecimal.valueOf(issuedCount), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal paidOrderRate = issuedCount == 0
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(paidOrderCount)
                    .divide(BigDecimal.valueOf(issuedCount), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("couponId", coupon.getId());
            item.put("name", coupon.getName());
            item.put("status", coupon.getStatus());
            item.put("type", coupon.getType());
            item.put("remainingCount", Math.max((coupon.getTotalCount() == null ? 0 : coupon.getTotalCount())
                    - (coupon.getUsedCount() == null ? 0 : coupon.getUsedCount()), 0));
            item.put("issuedCount", issuedCount);
            item.put("redeemedCount", redeemedCount);
            item.put("paidOrderCount", paidOrderCount);
            item.put("paidGmv", paidGmv);
            item.put("avgOrderAmount", avgOrderAmount);
            item.put("redeemRate", redeemRate);
            item.put("paidOrderRate", paidOrderRate);
            breakdown.add(item);

            totalIssued += issuedCount;
            totalRedeemed += redeemedCount;
            totalPaidOrders += paidOrderCount;
            totalPaidGmv = totalPaidGmv.add(paidGmv);
        }

        BigDecimal avgOrderAmount = totalPaidOrders == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalPaidGmv.divide(BigDecimal.valueOf(totalPaidOrders), 2, RoundingMode.HALF_UP);
        BigDecimal redeemRate = totalIssued == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(totalRedeemed)
                .divide(BigDecimal.valueOf(totalIssued), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal paidOrderRate = totalIssued == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(totalPaidOrders)
                .divide(BigDecimal.valueOf(totalIssued), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        summary.put("issuedCount", totalIssued);
        summary.put("redeemedCount", totalRedeemed);
        summary.put("paidOrderCount", totalPaidOrders);
        summary.put("paidGmv", totalPaidGmv.setScale(2, RoundingMode.HALF_UP));
        summary.put("avgOrderAmount", avgOrderAmount);
        summary.put("redeemRate", redeemRate);
        summary.put("paidOrderRate", paidOrderRate);

        breakdown.sort((left, right) -> {
            BigDecimal rightValue = (BigDecimal) right.getOrDefault("paidGmv", BigDecimal.ZERO);
            BigDecimal leftValue = (BigDecimal) left.getOrDefault("paidGmv", BigDecimal.ZERO);
            int compare = rightValue.compareTo(leftValue);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(
                    ((Number) right.getOrDefault("issuedCount", 0)).intValue(),
                    ((Number) left.getOrDefault("issuedCount", 0)).intValue());
        });

        snapshot.summary = summary;
        snapshot.trend = buildCouponTrend(cohortUserCoupons, paidOrdersByCouponId, issueStartTime, trendDays);
        snapshot.breakdown = breakdown;
        return snapshot;
    }

    private Map<String, Object> buildEmptyCouponDashboardSummary(int days) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("couponCount", 0);
        summary.put("activeCouponCount", 0);
        summary.put("issuedCount", 0);
        summary.put("redeemedCount", 0);
        summary.put("paidOrderCount", 0);
        summary.put("paidGmv", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        summary.put("avgOrderAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        summary.put("redeemRate", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        summary.put("paidOrderRate", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        summary.put("days", days);
        return summary;
    }

    private Map<String, Object> buildEmptyCouponTrend(int days) {
        Map<String, Object> trend = new LinkedHashMap<>();
        List<String> dates = new ArrayList<>();
        List<Integer> issuedCounts = new ArrayList<>();
        List<Integer> redeemedCounts = new ArrayList<>();
        List<Integer> paidOrderCounts = new ArrayList<>();
        List<BigDecimal> paidGmvAmounts = new ArrayList<>();

        if (days > 0) {
            LocalDate today = LocalDate.now();
            for (int i = days - 1; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                dates.add(day.toString().substring(5));
                issuedCounts.add(0);
                redeemedCounts.add(0);
                paidOrderCounts.add(0);
                paidGmvAmounts.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
        }

        trend.put("dates", dates);
        trend.put("issuedCounts", issuedCounts);
        trend.put("redeemedCounts", redeemedCounts);
        trend.put("paidOrderCounts", paidOrderCounts);
        trend.put("paidGmvAmounts", paidGmvAmounts);
        return trend;
    }

    private Map<String, Object> buildCouponTrend(List<UserCoupon> issuedCoupons,
                                                 Map<Long, List<Order>> paidOrdersByCouponId,
                                                 LocalDateTime issueStartTime,
                                                 int trendDays) {
        if (trendDays <= 0 || issueStartTime == null) {
            return buildEmptyCouponTrend(0);
        }

        LocalDate today = LocalDate.now();
        Map<String, Integer> issuedMap = new HashMap<>();
        Map<String, Integer> redeemedMap = new HashMap<>();
        Map<String, Integer> paidOrderMap = new HashMap<>();
        Map<String, BigDecimal> paidGmvMap = new HashMap<>();

        for (UserCoupon userCoupon : issuedCoupons) {
            if (userCoupon == null) {
                continue;
            }
            LocalDateTime issueTime = userCoupon.getCreateTime();
            if (issueTime != null) {
                issuedMap.merge(issueTime.toLocalDate().toString(), 1, Integer::sum);
            }
            if (Objects.equals(userCoupon.getStatus(), 1)) {
                LocalDateTime redeemTime = userCoupon.getUseTime();
                if (redeemTime != null) {
                    redeemedMap.merge(redeemTime.toLocalDate().toString(), 1, Integer::sum);
                }
            }
        }

        for (List<Order> orders : paidOrdersByCouponId.values()) {
            for (Order order : orders) {
                if (order == null) {
                    continue;
                }
                LocalDateTime time = order.getPayTime() != null ? order.getPayTime() : order.getCreateTime();
                if (time == null) {
                    continue;
                }
                String dateKey = time.toLocalDate().toString();
                paidOrderMap.merge(dateKey, 1, Integer::sum);
                paidGmvMap.merge(dateKey,
                        order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount(),
                        BigDecimal::add);
            }
        }

        List<String> dates = new ArrayList<>();
        List<Integer> issuedCounts = new ArrayList<>();
        List<Integer> redeemedCounts = new ArrayList<>();
        List<Integer> paidOrderCounts = new ArrayList<>();
        List<BigDecimal> paidGmvAmounts = new ArrayList<>();
        for (int i = trendDays - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String dateKey = day.toString();
            dates.add(dateKey.substring(5));
            issuedCounts.add(issuedMap.getOrDefault(dateKey, 0));
            redeemedCounts.add(redeemedMap.getOrDefault(dateKey, 0));
            paidOrderCounts.add(paidOrderMap.getOrDefault(dateKey, 0));
            paidGmvAmounts.add(paidGmvMap.getOrDefault(dateKey, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("dates", dates);
        trend.put("issuedCounts", issuedCounts);
        trend.put("redeemedCounts", redeemedCounts);
        trend.put("paidOrderCounts", paidOrderCounts);
        trend.put("paidGmvAmounts", paidGmvAmounts);
        return trend;
    }

    private int resolveInt(Object value, int defaultValue) {
        Integer parsed = resolveNullableInt(value);
        return parsed == null ? defaultValue : parsed;
    }

    private Integer resolveNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal resolveNullableBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private Set<Long> parseUserIds(Object rawUserIds) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (rawUserIds == null) {
            return result;
        }
        if (rawUserIds instanceof Collection) {
            for (Object item : (Collection<?>) rawUserIds) {
                Long value = parseLong(item);
                if (value != null && value > 0) {
                    result.add(value);
                }
            }
            return result;
        }
        if (rawUserIds instanceof String) {
            String[] values = ((String) rawUserIds).split("[,，;；\\s]+");
            for (String token : values) {
                Long value = parseLong(token);
                if (value != null && value > 0) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static class MerchantBehaviorContext {
        private final Map<Long, MerchantUserMetrics> userMetrics = new LinkedHashMap<>();
        private final Map<String, Long> behaviorCount = new HashMap<>();
        private final Map<String, Set<Long>> behaviorUsers = new HashMap<>();
    }

    private static class CouponConversionSnapshot {
        private Map<String, Object> summary = new LinkedHashMap<>();
        private Map<String, Object> trend = new LinkedHashMap<>();
        private List<Map<String, Object>> breakdown = new ArrayList<>();
    }

    private static class MerchantUserMetrics {
        private final Long userId;
        private User user;
        private String segmentCode;
        private String segmentName;
        private BigDecimal segmentConfidence = BigDecimal.ZERO;
        private int viewCount;
        private int cartCount;
        private int favoriteCount;
        private int purchaseCount;
        private int behaviorCount;
        private final Set<String> activeDays30d = new HashSet<>();
        private LocalDateTime lastBehaviorTime;
        private BigDecimal spend30d = BigDecimal.ZERO;
        private final Set<Long> orderIds30d = new HashSet<>();
        private int score;

        private MerchantUserMetrics(Long userId) {
            this.userId = userId;
        }
    }

    private Map<Long, BigDecimal> groupOrderAmounts(List<OrderItem> items) {
        Map<Long, BigDecimal> orderAmounts = new HashMap<>();
        for (OrderItem item : items) {
            orderAmounts.merge(item.getOrderId(), resolveItemAmount(item), BigDecimal::add);
        }
        return orderAmounts;
    }

    private BigDecimal resolveItemAmount(OrderItem item) {
        if (item.getSubtotal() != null) {
            return item.getSubtotal();
        }
        if (item.getPrice() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private Product buildSafeProductUpdate(Long id, Product source) {
        Product update = new Product();
        update.setId(id);
        update.setName(source.getName());
        update.setDescription(source.getDescription());
        update.setPrice(source.getPrice());
        update.setOriginalPrice(source.getOriginalPrice());
        update.setCategoryId(source.getCategoryId());
        update.setImage(source.getImage());
        update.setImages(source.getImages());
        update.setTags(source.getTags());
        update.setStock(source.getStock());
        return update;
    }

    private Set<Long> filterExclusiveMerchantOrderIds(Set<Long> merchantProductIds, Set<Long> candidateOrderIds) {
        if (merchantProductIds.isEmpty() || candidateOrderIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<OrderItem> allOrderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, candidateOrderIds));
        return allOrderItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId))
                .entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isEmpty()
                        && entry.getValue().stream().allMatch(item -> merchantProductIds.contains(item.getProductId())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
