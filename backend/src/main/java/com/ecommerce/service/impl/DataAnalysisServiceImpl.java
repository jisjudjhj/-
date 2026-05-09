package com.ecommerce.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.entity.AnalyticsAssociationRule;
import com.ecommerce.entity.AnalyticsBehaviorHeatmap;
import com.ecommerce.entity.AnalyticsFunnelDaily;
import com.ecommerce.entity.AnalyticsReportSnapshot;
import com.ecommerce.entity.AnalyticsRfmSegmentSnapshot;
import com.ecommerce.entity.AnalyticsRfmUserSnapshot;
import com.ecommerce.entity.AnalyticsSalesDaily;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.AnalyticsAssociationRuleMapper;
import com.ecommerce.mapper.AnalyticsBehaviorHeatmapMapper;
import com.ecommerce.mapper.AnalyticsFunnelDailyMapper;
import com.ecommerce.mapper.AnalyticsReportSnapshotMapper;
import com.ecommerce.mapper.AnalyticsRfmSegmentSnapshotMapper;
import com.ecommerce.mapper.AnalyticsRfmUserSnapshotMapper;
import com.ecommerce.mapper.AnalyticsSalesDailyMapper;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.service.DataAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataAnalysisServiceImpl implements DataAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DataAnalysisServiceImpl.class);
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final List<String> HEATMAP_DAYS = Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日");
    private static final List<String> REPORT_CODES = Arrays.asList("analysis_summary", "dashboard_overview");

    @Autowired
    private UserBehaviorMapper behaviorMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AnalyticsFunnelDailyMapper analyticsFunnelDailyMapper;

    @Autowired
    private AnalyticsRfmUserSnapshotMapper analyticsRfmUserSnapshotMapper;

    @Autowired
    private AnalyticsRfmSegmentSnapshotMapper analyticsRfmSegmentSnapshotMapper;

    @Autowired
    private AnalyticsAssociationRuleMapper analyticsAssociationRuleMapper;

    @Autowired
    private AnalyticsSalesDailyMapper analyticsSalesDailyMapper;

    @Autowired
    private AnalyticsBehaviorHeatmapMapper analyticsBehaviorHeatmapMapper;

    @Autowired
    private AnalyticsReportSnapshotMapper analyticsReportSnapshotMapper;

    @Value("${analytics.health.freshness-weight:0.7}")
    private double healthFreshnessWeight;

    @Value("${analytics.health.volume-weight:0.3}")
    private double healthVolumeWeight;

    // ==================== 漏斗分析 ====================

    @Override
    public Map<String, Object> funnelAnalysis() {
        Map<String, Object> snapshot = loadFunnelSnapshot();
        if (snapshot != null) {
            return snapshot;
        }

        long totalUsers = countRegularUsers();
        List<UserBehavior> allBehaviors = behaviorMapper.selectList(null);
        if (allBehaviors.isEmpty()) {
            return buildFunnelResult(Collections.emptySet(), Collections.emptySet(),
                    Collections.emptySet(), Collections.emptySet(), totalUsers, null);
        }

        Set<Long> regularUserIds = loadRegularUserIds();
        LocalDate latestBehaviorDate = allBehaviors.stream()
                .filter(item -> item != null
                        && item.getUserId() != null
                        && regularUserIds.contains(item.getUserId())
                        && item.getCreateTime() != null)
                .map(item -> item.getCreateTime().toLocalDate())
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (latestBehaviorDate == null) {
            return buildFunnelResult(Collections.emptySet(), Collections.emptySet(),
                    Collections.emptySet(), Collections.emptySet(), totalUsers, null);
        }

        Set<Long> viewUsers = new HashSet<>();
        Set<Long> cartUsers = new HashSet<>();
        Set<Long> favUsers  = new HashSet<>();
        Set<Long> buyUsers  = new HashSet<>();

        for (UserBehavior b : allBehaviors) {
            Long uid = b.getUserId();
            if (uid == null || !regularUserIds.contains(uid) || b.getCreateTime() == null
                    || !latestBehaviorDate.equals(b.getCreateTime().toLocalDate())) {
                continue;
            }
            switch (b.getBehaviorType()) {
                case "view":     viewUsers.add(uid); break;
                case "cart":     cartUsers.add(uid); break;
                case "favorite": favUsers.add(uid);  break;
                case "purchase": buyUsers.add(uid);  break;
            }
        }

        return buildFunnelResult(viewUsers, cartUsers, favUsers, buyUsers, totalUsers, latestBehaviorDate);
    }

    private Map<String, Object> buildFunnelStage(String name, long count, long total) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("name", name);
        stage.put("count", count);
        stage.put("rate", total == 0 ? 0 : round2((double) count / total * 100));
        return stage;
    }

    private Map<String, Object> buildFunnelResult(Set<Long> viewUsers,
                                                  Set<Long> cartUsers,
                                                  Set<Long> favoriteUsers,
                                                  Set<Long> purchaseUsers,
                                                  long totalUsers,
                                                  LocalDate snapshotDate) {
        return buildFunnelResultByCounts(
                viewUsers.size(),
                cartUsers.size(),
                favoriteUsers.size(),
                purchaseUsers.size(),
                totalUsers,
                snapshotDate,
                round2(percentage(intersectionSize(viewUsers, cartUsers), viewUsers.size())),
                round2(percentage(intersectionSize(cartUsers, purchaseUsers), cartUsers.size())),
                round2(percentage(intersectionSize(viewUsers, purchaseUsers), viewUsers.size()))
        );
    }

    private Map<String, Object> buildFunnelResultByCounts(long viewCount,
                                                          long cartCount,
                                                          long favoriteCount,
                                                          long purchaseCount,
                                                          long totalUsers,
                                                          LocalDate snapshotDate,
                                                          double viewToCart,
                                                          double cartToBuy,
                                                          double viewToBuy) {
        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(buildFunnelStage("浏览", viewCount, totalUsers));
        stages.add(buildFunnelStage("加购", cartCount, totalUsers));
        stages.add(buildFunnelStage("收藏", favoriteCount, totalUsers));
        stages.add(buildFunnelStage("购买", purchaseCount, totalUsers));

        Map<String, Object> conversion = new LinkedHashMap<>();
        conversion.put("viewToCart", viewToCart);
        conversion.put("cartToBuy", cartToBuy);
        conversion.put("viewToBuy", viewToBuy);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stages", stages);
        result.put("conversion", conversion);
        result.put("totalUsers", totalUsers);
        result.put("snapshotDate", snapshotDate);
        return result;
    }

    private Map<String, Object> buildFunnelResultByCounts(long viewCount,
                                                          long cartCount,
                                                          long favoriteCount,
                                                          long purchaseCount,
                                                          long totalUsers,
                                                          LocalDate snapshotDate) {
        return buildFunnelResultByCounts(
                viewCount,
                cartCount,
                favoriteCount,
                purchaseCount,
                totalUsers,
                snapshotDate,
                round2(percentage(cartCount, viewCount)),
                round2(percentage(purchaseCount, cartCount)),
                round2(percentage(purchaseCount, viewCount))
        );
    }

    private long intersectionSize(Set<Long> left, Set<Long> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0L;
        }
        Set<Long> smaller = left.size() <= right.size() ? left : right;
        Set<Long> larger = smaller == left ? right : left;
        return smaller.stream().filter(larger::contains).count();
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return (double) numerator / denominator * 100D;
    }

    private long countRegularUsers() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "user"));
    }

    private Set<Long> loadRegularUserIds() {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "user")
                        .select(User::getId))
                .stream()
                .map(User::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }

    // ==================== RFM 分群 ====================

    @Override
    public Map<String, Object> rfmSegmentation() {
        Map<String, Object> snapshot = loadRfmSnapshot();
        if (snapshot != null) {
            return snapshot;
        }

        LocalDate today = LocalDate.now();
        List<Order> paidOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().in(Order::getStatus, 1, 2, 3));

        if (paidOrders.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("segments", Collections.emptyList());
            empty.put("details", Collections.emptyList());
            Map<String, Object> thresholds = new LinkedHashMap<>();
            thresholds.put("recencyMedian", 0.0);
            thresholds.put("frequencyMedian", 0.0);
            thresholds.put("monetaryMedian", 0.0);
            empty.put("thresholds", thresholds);
            empty.put("totalAnalyzed", 0);
            empty.put("snapshotDate", today);
            return empty;
        }

        Map<Long, List<Order>> byUser = paidOrders.stream()
                .collect(Collectors.groupingBy(Order::getUserId));

        List<Map<String, Object>> rfmList = new ArrayList<>();

        for (Map.Entry<Long, List<Order>> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            List<Order> orders = entry.getValue();

            LocalDate lastOrderDate = orders.stream()
                    .map(this::resolveRfmOrderDate)
                    .filter(value -> value != null)
                    .max(Comparator.naturalOrder()).orElse(today);
            long recencyDays = ChronoUnit.DAYS.between(lastOrderDate, today);

            int frequency = orders.size();
            BigDecimal monetary = orders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", userId);
            item.put("recencyDays", recencyDays);
            item.put("frequency", frequency);
            item.put("monetary", monetary);
            rfmList.add(item);
        }

        double[] recencyArr  = rfmList.stream().mapToDouble(m -> ((Number)m.get("recencyDays")).doubleValue()).toArray();
        double[] frequencyArr = rfmList.stream().mapToDouble(m -> ((Number)m.get("frequency")).doubleValue()).toArray();
        double[] monetaryArr  = rfmList.stream().mapToDouble(m -> ((BigDecimal)m.get("monetary")).doubleValue()).toArray();

        double rMedian = median(recencyArr);
        double fMedian = median(frequencyArr);
        double mMedian = median(monetaryArr);

        Map<String, Integer> segmentCounts = new LinkedHashMap<>();
        for (Map<String, Object> item : rfmList) {
            int rScore = ((Number)item.get("recencyDays")).doubleValue() <= rMedian ? 1 : 0;
            int fScore = ((Number)item.get("frequency")).doubleValue() >= fMedian ? 1 : 0;
            int mScore = ((BigDecimal)item.get("monetary")).doubleValue() >= mMedian ? 1 : 0;
            String label = rfmLabel(rScore, fScore, mScore);
            item.put("rScore", rScore);
            item.put("fScore", fScore);
            item.put("mScore", mScore);
            item.put("segment", label);
            segmentCounts.merge(label, 1, Integer::sum);
        }

        rfmList.sort(Comparator
                .comparing((Map<String, Object> item) -> (BigDecimal) item.get("monetary"), this::compareDecimal)
                .reversed()
                .thenComparing(item -> ((Number) item.get("frequency")).intValue(), Comparator.reverseOrder())
                .thenComparing(item -> ((Number) item.get("recencyDays")).longValue()));

        List<Map<String, Object>> segments = new ArrayList<>();
        for (Map.Entry<String, Integer> e : segmentCounts.entrySet()) {
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("name", e.getKey());
            seg.put("count", e.getValue());
            seg.put("percentage", round2((double) e.getValue() / rfmList.size() * 100));
            segments.add(seg);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("segments", segments);
        result.put("details", rfmList.subList(0, Math.min(50, rfmList.size())));
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("recencyMedian", round2(rMedian));
        thresholds.put("frequencyMedian", round2(fMedian));
        thresholds.put("monetaryMedian", round2(mMedian));
        result.put("thresholds", thresholds);
        result.put("totalAnalyzed", rfmList.size());
        result.put("snapshotDate", today);
        return result;
    }

    private LocalDate resolveRfmOrderDate(Order order) {
        if (order == null) {
            return null;
        }
        if (order.getPayTime() != null) {
            return order.getPayTime().toLocalDate();
        }
        return order.getCreateTime() == null ? null : order.getCreateTime().toLocalDate();
    }

    private String rfmLabel(int r, int f, int m) {
        if (r == 1 && f == 1 && m == 1) return "重要价值客户";
        if (r == 1 && f == 1 && m == 0) return "重要发展客户";
        if (r == 1 && f == 0 && m == 1) return "重要保持客户";
        if (r == 1 && f == 0 && m == 0) return "新客户";
        if (r == 0 && f == 1 && m == 1) return "重要挽留客户";
        if (r == 0 && f == 1 && m == 0) return "一般客户";
        if (r == 0 && f == 0 && m == 1) return "流失高价值客户";
        return "流失客户";
    }

    // ==================== 关联规则挖掘 ====================

    @Override
    public Map<String, Object> associationRules(int minSupport, double minConfidence) {
        Map<String, Object> snapshot = loadAssociationSnapshot(minSupport, minConfidence);
        if (snapshot != null) {
            return snapshot;
        }

        List<Order> paidOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().in(Order::getStatus, 1, 2, 3));

        Map<Long, Set<Long>> orderProducts = new LinkedHashMap<>();
        for (Order order : paidOrders) {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
            Set<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toSet());
            if (productIds.size() >= 2) {
                orderProducts.put(order.getId(), productIds);
            }
        }

        int totalTransactions = orderProducts.size();
        if (totalTransactions == 0) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("rules", Collections.emptyList());
            empty.put("totalTransactions", 0);
            return empty;
        }

        Map<String, Integer> pairCount = new HashMap<>();
        Map<Long, Integer> singleCount = new HashMap<>();

        for (Set<Long> products : orderProducts.values()) {
            for (Long pid : products) {
                singleCount.merge(pid, 1, Integer::sum);
            }
            List<Long> sorted = new ArrayList<>(products);
            Collections.sort(sorted);
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    String key = sorted.get(i) + "-" + sorted.get(j);
                    pairCount.merge(key, 1, Integer::sum);
                }
            }
        }

        List<Map<String, Object>> rules = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : pairCount.entrySet()) {
            int support = entry.getValue();
            if (support < minSupport) continue;

            String[] ids = entry.getKey().split("-");
            Long a = Long.parseLong(ids[0]);
            Long b = Long.parseLong(ids[1]);
            int countA = singleCount.getOrDefault(a, 0);
            int countB = singleCount.getOrDefault(b, 0);

            double supportRate = (double) support / totalTransactions;
            double confidenceAB = countA == 0 ? 0 : (double) support / countA;
            double confidenceBA = countB == 0 ? 0 : (double) support / countB;
            double expectedAB = (double) countA / totalTransactions * countB / totalTransactions;
            double lift = expectedAB == 0 ? 0 : supportRate / expectedAB;

            if (confidenceAB >= minConfidence || confidenceBA >= minConfidence) {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("productA", a);
                rule.put("productB", b);
                rule.put("support", support);
                rule.put("supportRate", round4(supportRate));
                rule.put("confidenceAB", round4(confidenceAB));
                rule.put("confidenceBA", round4(confidenceBA));
                rule.put("lift", round2(lift));
                rules.add(rule);
            }
        }

        rules.sort((a, b) -> Double.compare(
                ((Number) b.get("lift")).doubleValue(),
                ((Number) a.get("lift")).doubleValue()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rules", rules.subList(0, Math.min(50, rules.size())));
        result.put("totalTransactions", totalTransactions);
        result.put("totalPairs", pairCount.size());
        result.put("filteredRules", rules.size());
        return result;
    }

    // ==================== 留存分析 ====================

    @Override
    public Map<String, Object> retentionAnalysis() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "user"));
        List<UserBehavior> allBehaviors = behaviorMapper.selectList(null);

        Map<String, Set<Long>> cohortUsers = new LinkedHashMap<>();
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");

        for (User u : users) {
            if (u.getCreateTime() != null) {
                String month = u.getCreateTime().format(monthFmt);
                cohortUsers.computeIfAbsent(month, k -> new HashSet<>()).add(u.getId());
            }
        }

        Map<Long, Set<String>> userActiveMonths = new HashMap<>();
        for (UserBehavior b : allBehaviors) {
            if (b.getCreateTime() != null) {
                String month = b.getCreateTime().format(monthFmt);
                userActiveMonths.computeIfAbsent(b.getUserId(), k -> new HashSet<>()).add(month);
            }
        }

        List<String> allMonths = new ArrayList<>(cohortUsers.keySet());
        Collections.sort(allMonths);

        List<Map<String, Object>> cohorts = new ArrayList<>();
        for (String regMonth : allMonths) {
            Set<Long> cohort = cohortUsers.get(regMonth);
            int cohortSize = cohort.size();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cohort", regMonth);
            row.put("size", cohortSize);

            List<Double> retentionRates = new ArrayList<>();
            for (int offset = 0; offset < allMonths.size(); offset++) {
                int idx = allMonths.indexOf(regMonth) + offset;
                if (idx >= allMonths.size()) break;
                String targetMonth = allMonths.get(idx);
                long activeCount = cohort.stream()
                        .filter(uid -> userActiveMonths.containsKey(uid)
                                && userActiveMonths.get(uid).contains(targetMonth))
                        .count();
                retentionRates.add(cohortSize == 0 ? 0 : round2((double) activeCount / cohortSize * 100));
            }
            row.put("retention", retentionRates);
            cohorts.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cohorts", cohorts);
        result.put("months", allMonths);
        return result;
    }

    // ==================== 销售趋势 + 移动平均 ====================

    @Override
    public Map<String, Object> salesTrendAnalysis() {
        Map<String, Object> snapshot = loadSalesTrendSnapshot();
        if (snapshot != null) {
            return snapshot;
        }

        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        List<Order> recentOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .in(Order::getStatus, 1, 2, 3)
                        .ge(Order::getCreateTime, startDate));

        Map<String, BigDecimal> dailyRevenue = new LinkedHashMap<>();
        Map<String, Integer> dailyCount = new LinkedHashMap<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = 29; i >= 0; i--) {
            String key = LocalDate.now().minusDays(i).format(dateFmt);
            dailyRevenue.put(key, BigDecimal.ZERO);
            dailyCount.put(key, 0);
        }

        for (Order o : recentOrders) {
            String key = o.getCreateTime().toLocalDate().format(dateFmt);
            dailyRevenue.merge(key, o.getTotalAmount(), BigDecimal::add);
            dailyCount.merge(key, 1, Integer::sum);
        }

        List<String> dates = new ArrayList<>(dailyRevenue.keySet());
        List<Double> revenues = dates.stream()
                .map(d -> dailyRevenue.get(d).doubleValue()).collect(Collectors.toList());
        List<Integer> counts = dates.stream().map(dailyCount::get).collect(Collectors.toList());

        List<Double> ma7 = movingAverage(revenues, 7);

        double totalRevenue = revenues.stream().mapToDouble(Double::doubleValue).sum();
        int totalOrders = counts.stream().mapToInt(Integer::intValue).sum();
        double avgOrderValue = totalOrders == 0 ? 0 : totalRevenue / totalOrders;

        double lastWeekRev = revenues.subList(Math.max(0, revenues.size()-7), revenues.size())
                .stream().mapToDouble(Double::doubleValue).sum();
        double prevWeekRev = revenues.size() >= 14
                ? revenues.subList(revenues.size()-14, revenues.size()-7)
                    .stream().mapToDouble(Double::doubleValue).sum()
                : 0;
        double weekOverWeek = prevWeekRev == 0 ? 0 : (lastWeekRev - prevWeekRev) / prevWeekRev * 100;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("revenues", revenues);
        result.put("orderCounts", counts);
        result.put("movingAverage7", ma7);
        result.put("totalRevenue", round2(totalRevenue));
        result.put("totalOrders", totalOrders);
        result.put("avgOrderValue", round2(avgOrderValue));
        result.put("weekOverWeek", round2(weekOverWeek));
        return result;
    }

    private List<Double> movingAverage(List<Double> data, int window) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            if (i < window - 1) {
                result.add(null);
            } else {
                double sum = 0;
                for (int j = i - window + 1; j <= i; j++) {
                    sum += data.get(j);
                }
                result.add(round2(sum / window));
            }
        }
        return result;
    }

    // ==================== 活跃度热力图 ====================

    @Override
    public Map<String, Object> activityHeatmap() {
        Map<String, Object> snapshot = loadHeatmapSnapshot();
        if (snapshot != null) {
            return snapshot;
        }

        List<UserBehavior> allBehaviors = behaviorMapper.selectList(null);

        int[][] heatmap = new int[7][24];
        for (UserBehavior b : allBehaviors) {
            if (b.getCreateTime() != null) {
                int dow = b.getCreateTime().getDayOfWeek().getValue() - 1; // 0=Mon
                int hour = b.getCreateTime().getHour();
                heatmap[dow][hour]++;
            }
        }

        List<List<Integer>> data = new ArrayList<>();
        int maxVal = 0;
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                data.add(Arrays.asList(h, d, heatmap[d][h]));
                maxVal = Math.max(maxVal, heatmap[d][h]);
            }
        }

        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        List<String> hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) hours.add(i + ":00");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", data);
        result.put("days", Arrays.asList(days));
        result.put("hours", hours);
        result.put("max", maxVal);
        return result;
    }

    // ==================== 综合概览 ====================

    @Override
    public Map<String, Object> analysisSummary() {
        Map<String, Object> snapshot = loadSummarySnapshot();
        if (snapshot != null) {
            return snapshot;
        }

        long totalBehaviors = behaviorMapper.selectCount(null);
        long totalOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().in(Order::getStatus, 1, 2, 3));
        long totalUsers = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, "user"));

        List<Map<String, Object>> behaviorStats = behaviorMapper.selectBehaviorStats();

        long viewCount = 0, cartCount = 0, favCount = 0, purchaseCount = 0;
        for (Map<String, Object> stat : behaviorStats) {
            String type = (String) stat.get("behavior_type");
            long count = ((Number) stat.get("count")).longValue();
            switch (type) {
                case "view":     viewCount = count; break;
                case "cart":     cartCount = count; break;
                case "favorite": favCount = count;  break;
                case "purchase": purchaseCount = count; break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBehaviorRecords", totalBehaviors);
        result.put("totalPaidOrders", totalOrders);
        result.put("totalActiveUsers", totalUsers);
        result.put("viewCount", viewCount);
        result.put("cartCount", cartCount);
        result.put("favoriteCount", favCount);
        result.put("purchaseCount", purchaseCount);
        result.put("overallConversionRate",
                viewCount == 0 ? 0 : round2((double) purchaseCount / viewCount * 100));
        return result;
    }

    @Override
    public Map<String, Object> analysisHealthScore() {
        LocalDate today = LocalDate.now();

        AnalyticsSalesDaily latestSales = firstOrNull(analyticsSalesDailyMapper.selectList(
                new LambdaQueryWrapper<AnalyticsSalesDaily>()
                        .orderByDesc(AnalyticsSalesDaily::getStatDate)
                        .last("LIMIT 1")));
        AnalyticsFunnelDaily latestFunnel = firstOrNull(analyticsFunnelDailyMapper.selectList(
                new LambdaQueryWrapper<AnalyticsFunnelDaily>()
                        .orderByDesc(AnalyticsFunnelDaily::getStatDate)
                        .last("LIMIT 1")));
        AnalyticsRfmUserSnapshot latestRfm = firstOrNull(analyticsRfmUserSnapshotMapper.selectList(
                new LambdaQueryWrapper<AnalyticsRfmUserSnapshot>()
                        .orderByDesc(AnalyticsRfmUserSnapshot::getSnapshotDate)
                        .last("LIMIT 1")));
        AnalyticsAssociationRule latestAssociation = firstOrNull(analyticsAssociationRuleMapper.selectList(
                new LambdaQueryWrapper<AnalyticsAssociationRule>()
                        .orderByDesc(AnalyticsAssociationRule::getSnapshotDate)
                        .last("LIMIT 1")));
        AnalyticsBehaviorHeatmap latestHeatmap = firstOrNull(analyticsBehaviorHeatmapMapper.selectList(
                new LambdaQueryWrapper<AnalyticsBehaviorHeatmap>()
                        .orderByDesc(AnalyticsBehaviorHeatmap::getStatDate)
                        .last("LIMIT 1")));

        LocalDate salesDate = latestSales == null ? null : latestSales.getStatDate();
        LocalDate funnelDate = latestFunnel == null ? null : latestFunnel.getStatDate();
        LocalDate rfmDate = latestRfm == null ? null : latestRfm.getSnapshotDate();
        LocalDate associationDate = latestAssociation == null ? null : latestAssociation.getSnapshotDate();
        LocalDate heatmapDate = latestHeatmap == null ? null : latestHeatmap.getStatDate();

        long salesRows = salesDate == null ? 0L : analyticsSalesDailyMapper.selectCount(
                new LambdaQueryWrapper<AnalyticsSalesDaily>().eq(AnalyticsSalesDaily::getStatDate, salesDate));
        long funnelRows = funnelDate == null ? 0L : analyticsFunnelDailyMapper.selectCount(
                new LambdaQueryWrapper<AnalyticsFunnelDaily>().eq(AnalyticsFunnelDaily::getStatDate, funnelDate));
        long rfmRows = rfmDate == null ? 0L : analyticsRfmUserSnapshotMapper.selectCount(
                new LambdaQueryWrapper<AnalyticsRfmUserSnapshot>().eq(AnalyticsRfmUserSnapshot::getSnapshotDate, rfmDate));
        long associationRows = associationDate == null ? 0L : analyticsAssociationRuleMapper.selectCount(
                new LambdaQueryWrapper<AnalyticsAssociationRule>().eq(AnalyticsAssociationRule::getSnapshotDate, associationDate));
        long heatmapRows = heatmapDate == null ? 0L : analyticsBehaviorHeatmapMapper.selectCount(
                new LambdaQueryWrapper<AnalyticsBehaviorHeatmap>()
                        .eq(AnalyticsBehaviorHeatmap::getStatDate, heatmapDate)
                        .eq(AnalyticsBehaviorHeatmap::getBehaviorType, "all"));
        if (heatmapRows <= 0 && heatmapDate != null) {
            heatmapRows = analyticsBehaviorHeatmapMapper.selectCount(
                    new LambdaQueryWrapper<AnalyticsBehaviorHeatmap>()
                            .eq(AnalyticsBehaviorHeatmap::getStatDate, heatmapDate));
        }

        List<Map<String, Object>> modules = new ArrayList<>();
        modules.add(buildHealthModule("sales_daily", "销售日汇总", salesDate, salesRows, 1, today, 0.24));
        modules.add(buildHealthModule("funnel_daily", "行为漏斗", funnelDate, funnelRows, 1, today, 0.20));
        modules.add(buildHealthModule("rfm_snapshot", "RFM 分群快照", rfmDate, rfmRows, 100, today, 0.22));
        modules.add(buildHealthModule("association_rule", "关联规则快照", associationDate, associationRows, 20, today, 0.16));
        modules.add(buildHealthModule("behavior_heatmap", "行为热力图", heatmapDate, heatmapRows, 24, today, 0.18));

        double weightedScore = 0D;
        double weightTotal = 0D;
        List<String> suggestions = new ArrayList<>();
        List<Map<String, Object>> riskModules = new ArrayList<>();
        for (Map<String, Object> module : modules) {
            double score = ((Number) module.getOrDefault("score", 0D)).doubleValue();
            double weight = ((Number) module.getOrDefault("weight", 0D)).doubleValue();
            weightedScore += score * weight;
            weightTotal += weight;
            if (score < 70D) {
                suggestions.add("建议优先修复 " + module.get("label") + "，当前得分 " + round2(score));
            }
            if (score < 55D) {
                riskModules.add(module);
            }
        }
        double overallScore = weightTotal <= 0D ? 0D : round2(weightedScore / weightTotal);
        long staleModules = modules.stream()
                .filter(module -> readLongValue(module.get("staleDays")) > 3L)
                .count();
        long weakVolumeModules = modules.stream()
                .filter(module -> readDoubleValue(module.get("volumeScore")) < 60D)
                .count();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("score", overallScore);
        payload.put("level", resolveHealthLevel(overallScore));
        payload.put("generatedAt", LocalDateTime.now());
        payload.put("modules", modules);
        payload.put("healthyModules", modules.stream()
                .filter(module -> ((Number) module.getOrDefault("score", 0D)).doubleValue() >= 80D)
                .count());
        payload.put("moduleCount", modules.size());
        payload.put("staleModules", staleModules);
        payload.put("weakVolumeModules", weakVolumeModules);
        payload.put("criticalModules", riskModules);
        payload.put("suggestions", suggestions);
        payload.put("governanceAdvice", buildHealthGovernanceAdvice(overallScore, staleModules, weakVolumeModules));
        payload.put("scoreRule", "score = freshnessScore*" + round2(resolveFreshnessWeight())
                + " + volumeScore*" + round2(resolveVolumeWeight()) + ", overall 为加权平均");
        return payload;
    }

    private Map<String, Object> buildHealthModule(String code,
                                                  String label,
                                                  LocalDate latestDate,
                                                  long latestRows,
                                                  long expectedRows,
                                                  LocalDate today,
                                                  double weight) {
        long staleDays = latestDate == null ? Integer.MAX_VALUE : Math.max(0L, ChronoUnit.DAYS.between(latestDate, today));
        double freshnessScore = calcFreshnessScore(staleDays);
        double volumeScore = calcVolumeScore(latestRows, expectedRows);
        double score = round2(freshnessScore * resolveFreshnessWeight() + volumeScore * resolveVolumeWeight());

        Map<String, Object> module = new LinkedHashMap<>();
        module.put("code", code);
        module.put("label", label);
        module.put("score", score);
        module.put("level", resolveHealthLevel(score));
        module.put("weight", weight);
        module.put("latestDate", latestDate);
        module.put("staleDays", staleDays == Integer.MAX_VALUE ? null : staleDays);
        module.put("latestRows", latestRows);
        module.put("expectedRows", expectedRows);
        module.put("freshnessScore", round2(freshnessScore));
        module.put("volumeScore", round2(volumeScore));
        return module;
    }

    private double calcFreshnessScore(long staleDays) {
        if (staleDays <= 1L) {
            return 100D;
        }
        if (staleDays <= 3L) {
            return 88D;
        }
        if (staleDays <= 7L) {
            return 72D;
        }
        if (staleDays <= 14L) {
            return 55D;
        }
        return 30D;
    }

    private double calcVolumeScore(long latestRows, long expectedRows) {
        long safeExpected = Math.max(1L, expectedRows);
        double ratio = (double) Math.max(0L, latestRows) / (double) safeExpected;
        return Math.min(100D, Math.max(0D, ratio * 100D));
    }

    private double resolveFreshnessWeight() {
        double safeFreshnessWeight = healthFreshnessWeight;
        double safeVolumeWeight = healthVolumeWeight;
        if (safeFreshnessWeight <= 0D || safeVolumeWeight <= 0D) {
            return 0.7D;
        }
        double total = safeFreshnessWeight + safeVolumeWeight;
        if (total <= 0D) {
            return 0.7D;
        }
        return safeFreshnessWeight / total;
    }

    private double resolveVolumeWeight() {
        double freshnessWeight = resolveFreshnessWeight();
        return Math.max(0D, 1D - freshnessWeight);
    }

    private List<String> buildHealthGovernanceAdvice(double overallScore,
                                                     long staleModules,
                                                     long weakVolumeModules) {
        List<String> advice = new ArrayList<>();
        if (overallScore < 70D) {
            advice.add("建议先恢复离线任务稳定性，再逐步推进模型与看板迭代，避免基于脏数据运营。");
        }
        if (staleModules > 0) {
            advice.add("存在 " + staleModules + " 个模块新鲜度不足，建议优先排查调度失败、上游延迟和 CDC 卡点。");
        }
        if (weakVolumeModules > 0) {
            advice.add("存在 " + weakVolumeModules + " 个模块数据量偏低，建议核查分区、过滤条件和写入链路。");
        }
        if (advice.isEmpty()) {
            advice.add("分析链路整体稳定，可重点优化分群标签细粒度与实时画像回流速度。");
        }
        return advice;
    }

    private long readLongValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private double readDoubleValue(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private String resolveHealthLevel(double score) {
        if (score >= 90D) {
            return "excellent";
        }
        if (score >= 80D) {
            return "good";
        }
        if (score >= 70D) {
            return "fair";
        }
        if (score >= 55D) {
            return "warning";
        }
        return "critical";
    }

    // ==================== 快照读取 ====================

    private Map<String, Object> loadFunnelSnapshot() {
        try {
            AnalyticsFunnelDaily snapshot = firstOrNull(analyticsFunnelDailyMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsFunnelDaily>()
                            .orderByDesc(AnalyticsFunnelDaily::getStatDate)
                            .last("LIMIT 1")));
            if (snapshot == null) {
                return null;
            }

            long totalUsers = countRegularUsers();
            if (!isUsableFunnelSnapshot(snapshot, totalUsers)) {
                log.warn("[DataAnalysis] Skip invalid funnel snapshot at date {}, fallback to live calculation.",
                        snapshot.getStatDate());
                return null;
            }

            return buildFunnelResultByCounts(
                    nullSafeLong(snapshot.getViewUserCount()),
                    nullSafeLong(snapshot.getCartUserCount()),
                    nullSafeLong(snapshot.getFavoriteUserCount()),
                    nullSafeLong(snapshot.getPurchaseUserCount()),
                    totalUsers,
                    snapshot.getStatDate(),
                    round2(decimalToDouble(snapshot.getViewToCartRate())),
                    round2(decimalToDouble(snapshot.getCartToPurchaseRate())),
                    round2(decimalToDouble(snapshot.getViewToPurchaseRate()))
            );
        } catch (Exception exception) {
            log.warn("[DataAnalysis] Failed to load funnel snapshot, fallback to live calculation: {}", exception.getMessage());
            return null;
        }
    }

    private boolean isUsableFunnelSnapshot(AnalyticsFunnelDaily snapshot, long totalUsers) {
        long viewCount = nullSafeLong(snapshot.getViewUserCount());
        long cartCount = nullSafeLong(snapshot.getCartUserCount());
        long favoriteCount = nullSafeLong(snapshot.getFavoriteUserCount());
        long purchaseCount = nullSafeLong(snapshot.getPurchaseUserCount());
        if (viewCount < 0 || cartCount < 0 || favoriteCount < 0 || purchaseCount < 0) {
            return false;
        }
        if (totalUsers > 0 && (viewCount > totalUsers || cartCount > totalUsers
                || favoriteCount > totalUsers || purchaseCount > totalUsers)) {
            return false;
        }
        if (cartCount > viewCount || purchaseCount > viewCount || purchaseCount > cartCount) {
            return false;
        }
        return isPercentage(snapshot.getViewToCartRate())
                && isPercentage(snapshot.getCartToPurchaseRate())
                && isPercentage(snapshot.getViewToPurchaseRate());
    }

    private boolean isPercentage(BigDecimal value) {
        if (value == null) {
            return true;
        }
        return value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(new BigDecimal("100")) <= 0;
    }

    private Map<String, Object> loadRfmSnapshot() {
        try {
            AnalyticsRfmUserSnapshot latest = firstOrNull(analyticsRfmUserSnapshotMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsRfmUserSnapshot>()
                            .orderByDesc(AnalyticsRfmUserSnapshot::getSnapshotDate)
                            .last("LIMIT 1")));
            if (latest == null || latest.getSnapshotDate() == null) {
                return null;
            }

            LocalDate snapshotDate = latest.getSnapshotDate();
            List<AnalyticsRfmUserSnapshot> userSnapshots = analyticsRfmUserSnapshotMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsRfmUserSnapshot>()
                            .eq(AnalyticsRfmUserSnapshot::getSnapshotDate, snapshotDate));
            if (userSnapshots.isEmpty()) {
                return null;
            }

            List<AnalyticsRfmSegmentSnapshot> segmentSnapshots = analyticsRfmSegmentSnapshotMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsRfmSegmentSnapshot>()
                            .eq(AnalyticsRfmSegmentSnapshot::getSnapshotDate, snapshotDate)
                            .orderByDesc(AnalyticsRfmSegmentSnapshot::getUserCount));

            List<Map<String, Object>> segments = new ArrayList<>();
            for (AnalyticsRfmSegmentSnapshot segmentSnapshot : segmentSnapshots) {
                Map<String, Object> segment = new LinkedHashMap<>();
                segment.put("name", segmentSnapshot.getSegmentName());
                segment.put("count", nullSafeLong(segmentSnapshot.getUserCount()));
                segment.put("percentage", round2(decimalToDouble(segmentSnapshot.getPercentage())));
                segments.add(segment);
            }

            double[] recencyArr = userSnapshots.stream()
                    .mapToDouble(item -> item.getRecencyDays() == null ? 0 : item.getRecencyDays())
                    .toArray();
            double[] frequencyArr = userSnapshots.stream()
                    .mapToDouble(item -> item.getFrequencyCount() == null ? 0 : item.getFrequencyCount())
                    .toArray();
            double[] monetaryArr = userSnapshots.stream()
                    .mapToDouble(item -> decimalToDouble(item.getMonetaryAmount()))
                    .toArray();

            Map<String, Object> thresholds = new LinkedHashMap<>();
            thresholds.put("recencyMedian", round2(median(recencyArr)));
            thresholds.put("frequencyMedian", round2(median(frequencyArr)));
            thresholds.put("monetaryMedian", round2(median(monetaryArr)));

            List<Map<String, Object>> details = userSnapshots.stream()
                    .sorted(Comparator
                            .comparing(AnalyticsRfmUserSnapshot::getMonetaryAmount, this::compareDecimal)
                            .reversed()
                            .thenComparing(AnalyticsRfmUserSnapshot::getFrequencyCount, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(50)
                    .map(item -> {
                        Map<String, Object> detail = new LinkedHashMap<>();
                        detail.put("userId", item.getUserId());
                        detail.put("recencyDays", item.getRecencyDays());
                        detail.put("frequency", item.getFrequencyCount());
                        detail.put("monetary", item.getMonetaryAmount() == null ? BigDecimal.ZERO : item.getMonetaryAmount());
                        detail.put("rScore", item.getRScore());
                        detail.put("fScore", item.getFScore());
                        detail.put("mScore", item.getMScore());
                        detail.put("segment", item.getSegmentName());
                        return detail;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("segments", segments);
            result.put("details", details);
            result.put("thresholds", thresholds);
            result.put("totalAnalyzed", userSnapshots.size());
            result.put("snapshotDate", snapshotDate);
            return result;
        } catch (Exception exception) {
            log.warn("[DataAnalysis] Failed to load RFM snapshot, fallback to live calculation: {}", exception.getMessage());
            return null;
        }
    }

    private Map<String, Object> loadAssociationSnapshot(int minSupport, double minConfidence) {
        try {
            AnalyticsAssociationRule latest = firstOrNull(analyticsAssociationRuleMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsAssociationRule>()
                            .orderByDesc(AnalyticsAssociationRule::getSnapshotDate)
                            .orderByDesc(AnalyticsAssociationRule::getRankNo)
                            .last("LIMIT 1")));
            if (latest == null || latest.getSnapshotDate() == null) {
                return null;
            }

            LocalDate snapshotDate = latest.getSnapshotDate();
            List<AnalyticsAssociationRule> rows = analyticsAssociationRuleMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsAssociationRule>()
                            .eq(AnalyticsAssociationRule::getSnapshotDate, snapshotDate)
                            .orderByDesc(AnalyticsAssociationRule::getLift)
                            .orderByAsc(AnalyticsAssociationRule::getRankNo));
            if (rows.isEmpty()) {
                return null;
            }

            Map<String, Map<String, Object>> mergedRules = new LinkedHashMap<>();
            int totalTransactions = 0;
            for (AnalyticsAssociationRule row : rows) {
                long lhs = row.getLhsProductId() == null ? 0L : row.getLhsProductId();
                long rhs = row.getRhsProductId() == null ? 0L : row.getRhsProductId();
                long productA = Math.min(lhs, rhs);
                long productB = Math.max(lhs, rhs);
                String key = productA + ":" + productB;

                Map<String, Object> rule = mergedRules.computeIfAbsent(key, ignored -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productA", productA);
                    item.put("productB", productB);
                    item.put("support", nullSafeLong(row.getSupportCount()));
                    item.put("supportRate", round4(decimalToDouble(row.getSupportRate())));
                    item.put("confidenceAB", 0.0);
                    item.put("confidenceBA", 0.0);
                    item.put("lift", round2(decimalToDouble(row.getLift())));
                    return item;
                });

                if (lhs == productA && rhs == productB) {
                    rule.put("confidenceAB", round4(decimalToDouble(row.getConfidence())));
                } else {
                    rule.put("confidenceBA", round4(decimalToDouble(row.getConfidence())));
                }

                if (totalTransactions == 0
                        && row.getSupportRate() != null
                        && row.getSupportRate().compareTo(BigDecimal.ZERO) > 0
                        && row.getSupportCount() != null) {
                    totalTransactions = BigDecimal.valueOf(row.getSupportCount())
                            .divide(row.getSupportRate(), 0, RoundingMode.HALF_UP)
                            .intValue();
                }
            }

            List<Map<String, Object>> rules = mergedRules.values().stream()
                    .filter(item -> ((Number) item.get("support")).longValue() >= minSupport)
                    .filter(item -> ((Number) item.get("confidenceAB")).doubleValue() >= minConfidence
                            || ((Number) item.get("confidenceBA")).doubleValue() >= minConfidence)
                    .sorted((left, right) -> Double.compare(
                            ((Number) right.get("lift")).doubleValue(),
                            ((Number) left.get("lift")).doubleValue()))
                    .limit(50)
                    .collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rules", rules);
            result.put("totalTransactions", totalTransactions);
            result.put("totalPairs", mergedRules.size());
            result.put("filteredRules", rules.size());
            result.put("snapshotDate", snapshotDate);
            return result;
        } catch (Exception exception) {
            log.warn("[DataAnalysis] Failed to load association snapshot, fallback to live calculation: {}", exception.getMessage());
            return null;
        }
    }

    private Map<String, Object> loadSalesTrendSnapshot() {
        try {
            List<AnalyticsSalesDaily> actualRows = analyticsSalesDailyMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsSalesDaily>()
                            .eq(AnalyticsSalesDaily::getIsForecast, 0)
                            .orderByDesc(AnalyticsSalesDaily::getStatDate)
                            .last("LIMIT 30"));
            if (actualRows.isEmpty()) {
                return null;
            }

            Collections.reverse(actualRows);

            List<String> dates = new ArrayList<>();
            List<Double> revenues = new ArrayList<>();
            List<Integer> orderCounts = new ArrayList<>();
            List<Double> movingAverage7 = new ArrayList<>();
            double totalRevenue = 0;
            int totalOrders = 0;

            for (AnalyticsSalesDaily row : actualRows) {
                dates.add(row.getStatDate().format(SHORT_DATE_FORMATTER));
                double revenue = decimalToDouble(row.getRevenue());
                revenues.add(revenue);
                int orderCount = (int) nullSafeLong(row.getPaidOrderCount());
                orderCounts.add(orderCount);
                movingAverage7.add(row.getMovingAvg7d() == null ? null : round2(decimalToDouble(row.getMovingAvg7d())));
                totalRevenue += revenue;
                totalOrders += orderCount;
            }

            if (movingAverage7.stream().allMatch(value -> value == null)) {
                movingAverage7 = movingAverage(revenues, 7);
            }

            double avgOrderValue = totalOrders == 0 ? 0 : totalRevenue / totalOrders;
            double weekOverWeek = 0;
            AnalyticsSalesDaily latestActual = actualRows.get(actualRows.size() - 1);
            if (latestActual.getWeekOverWeek() != null) {
                weekOverWeek = decimalToDouble(latestActual.getWeekOverWeek());
            } else if (revenues.size() >= 14) {
                double lastWeekRevenue = revenues.subList(revenues.size() - 7, revenues.size()).stream()
                        .mapToDouble(Double::doubleValue)
                        .sum();
                double previousWeekRevenue = revenues.subList(revenues.size() - 14, revenues.size() - 7).stream()
                        .mapToDouble(Double::doubleValue)
                        .sum();
                weekOverWeek = previousWeekRevenue == 0 ? 0 : (lastWeekRevenue - previousWeekRevenue) / previousWeekRevenue * 100;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dates", dates);
            result.put("revenues", revenues);
            result.put("orderCounts", orderCounts);
            result.put("movingAverage7", movingAverage7);
            result.put("totalRevenue", round2(totalRevenue));
            result.put("totalOrders", totalOrders);
            result.put("avgOrderValue", round2(avgOrderValue));
            result.put("weekOverWeek", round2(weekOverWeek));
            result.put("snapshotDate", latestActual.getStatDate());
            return result;
        } catch (Exception exception) {
            log.warn("[DataAnalysis] Failed to load sales trend snapshot, fallback to live calculation: {}", exception.getMessage());
            return null;
        }
    }

    private Map<String, Object> loadHeatmapSnapshot() {
        try {
            AnalyticsBehaviorHeatmap latest = firstOrNull(analyticsBehaviorHeatmapMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsBehaviorHeatmap>()
                            .orderByDesc(AnalyticsBehaviorHeatmap::getStatDate)
                            .last("LIMIT 1")));
            if (latest == null || latest.getStatDate() == null) {
                return null;
            }

            LocalDate snapshotDate = latest.getStatDate();
            List<AnalyticsBehaviorHeatmap> rows = analyticsBehaviorHeatmapMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsBehaviorHeatmap>()
                            .eq(AnalyticsBehaviorHeatmap::getStatDate, snapshotDate)
                            .eq(AnalyticsBehaviorHeatmap::getBehaviorType, "all"));
            if (rows.isEmpty()) {
                rows = analyticsBehaviorHeatmapMapper.selectList(
                        new LambdaQueryWrapper<AnalyticsBehaviorHeatmap>()
                                .eq(AnalyticsBehaviorHeatmap::getStatDate, snapshotDate));
            }
            if (rows.isEmpty()) {
                return null;
            }

            int[][] heatmap = new int[7][24];
            for (AnalyticsBehaviorHeatmap row : rows) {
                int dayIndex = row.getDayOfWeek() == null ? -1 : row.getDayOfWeek() - 1;
                int hour = row.getHourOfDay() == null ? -1 : row.getHourOfDay();
                if (dayIndex < 0 || dayIndex >= 7 || hour < 0 || hour >= 24) {
                    continue;
                }
                heatmap[dayIndex][hour] += (int) nullSafeLong(row.getEventCount());
            }

            List<List<Integer>> data = new ArrayList<>();
            int maxValue = 0;
            for (int day = 0; day < 7; day++) {
                for (int hour = 0; hour < 24; hour++) {
                    data.add(Arrays.asList(hour, day, heatmap[day][hour]));
                    maxValue = Math.max(maxValue, heatmap[day][hour]);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            result.put("days", HEATMAP_DAYS);
            result.put("hours", buildHourLabels());
            result.put("max", maxValue);
            result.put("snapshotDate", snapshotDate);
            return result;
        } catch (Exception exception) {
            log.warn("[DataAnalysis] Failed to load heatmap snapshot, fallback to live calculation: {}", exception.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadSummarySnapshot() {
        for (String reportCode : REPORT_CODES) {
            try {
                AnalyticsReportSnapshot snapshot = firstOrNull(analyticsReportSnapshotMapper.selectList(
                        new LambdaQueryWrapper<AnalyticsReportSnapshot>()
                                .eq(AnalyticsReportSnapshot::getReportCode, reportCode)
                                .orderByDesc(AnalyticsReportSnapshot::getSnapshotDate)
                                .last("LIMIT 1")));
                if (snapshot == null || snapshot.getReportData() == null || snapshot.getReportData().trim().isEmpty()) {
                    continue;
                }

                Object parsed = JSON.parse(snapshot.getReportData());
                if (parsed instanceof Map) {
                    Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) parsed);
                    result.put("snapshotDate", snapshot.getSnapshotDate());
                    result.put("reportCode", snapshot.getReportCode());
                    return result;
                }
            } catch (Exception exception) {
                log.warn("[DataAnalysis] Failed to load report snapshot {}: {}", reportCode, exception.getMessage());
            }
        }
        return null;
    }

    // ==================== 工具方法 ====================

    private long nullSafeLong(Long value) {
        return value == null ? 0L : value;
    }

    private double decimalToDouble(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private int compareDecimal(BigDecimal left, BigDecimal right) {
        BigDecimal safeLeft = left == null ? BigDecimal.ZERO : left;
        BigDecimal safeRight = right == null ? BigDecimal.ZERO : right;
        return safeLeft.compareTo(safeRight);
    }

    private List<String> buildHourLabels() {
        List<String> hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            hours.add(i + ":00");
        }
        return hours;
    }

    private <T> T firstOrNull(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private double round2(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double round4(double val) {
        return BigDecimal.valueOf(val).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private double median(double[] arr) {
        double[] sorted = arr.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n == 0) return 0;
        return n % 2 == 0 ? (sorted[n/2-1] + sorted[n/2]) / 2.0 : sorted[n/2];
    }
}
