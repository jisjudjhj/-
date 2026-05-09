package com.ecommerce.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.dto.AnalyticsKmeansTriggerDTO;
import com.ecommerce.entity.AnalyticsJobLog;
import com.ecommerce.entity.AnalyticsKmeansSegment;
import com.ecommerce.entity.AnalyticsKmeansTask;
import com.ecommerce.entity.AnalyticsKmeansUserResult;
import com.ecommerce.mapper.AnalyticsJobLogMapper;
import com.ecommerce.mapper.AnalyticsKmeansSegmentMapper;
import com.ecommerce.mapper.AnalyticsKmeansTaskMapper;
import com.ecommerce.mapper.AnalyticsKmeansUserResultMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.UserClusterAnalysisService;
import com.ecommerce.utils.AnalyticsKmeansSegmentProfileUtil;
import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class UserClusterAnalysisServiceImpl implements UserClusterAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(UserClusterAnalysisServiceImpl.class);

    private static final String TASK_STATUS_SUCCESS = "success";
    private static final String TASK_STATUS_RUNNING = "running";
    private static final String JOB_NAME_KMEANS = "kmeans_user_cluster";
    private static final String CACHE_KEY_LATEST_TASK = "analytics:kmeans:latest:task";
    private static final String CACHE_KEY_LATEST_SUMMARY = "analytics:kmeans:latest:summary";
    private static final String CACHE_KEY_LATEST_SEGMENTS = "analytics:kmeans:latest:segments";
    private static final long CACHE_TTL_MINUTES = 30L;
    private static final String CACHE_STRATEGY = "mysql_source_of_truth_redis_hot_cache";
    private static final String STORAGE_RULE = "mysql_fact_redis_hot_cache";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_K = 3;
    private static final int DEFAULT_MIN_K = 2;
    private static final int DEFAULT_MAX_K = 6;

    @Autowired
    private AnalyticsKmeansTaskMapper taskMapper;

    @Autowired
    private AnalyticsKmeansSegmentMapper segmentMapper;

    @Autowired
    private AnalyticsKmeansUserResultMapper userResultMapper;

    @Autowired
    private AnalyticsJobLogMapper jobLogMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserBehaviorMapper userBehaviorMapper;

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @Value("${analytics.kmeans.runner.shell:powershell}")
    private String runnerShell;

    @Value("${analytics.kmeans.runner.script-path:}")
    private String runnerScriptPath;

    private final AtomicBoolean manualLaunchInProgress = new AtomicBoolean(false);

    private volatile Map<String, Object> lastManualLaunch = Collections.emptyMap();

    @Override
    public Map<String, Object> getLatestTask() {
        Object cached = redisUtil.get(CACHE_KEY_LATEST_TASK);
        Map<String, Object> cachedTask = parseMapCache(cached);
        if (cachedTask != null) {
            return withMeta(cachedTask, "redis");
        }

        AnalyticsKmeansTask task = findLatestSuccessfulTask();
        if (task == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> payload = buildTaskPayload(task);
        payload.put("cacheStrategy", CACHE_STRATEGY);
        cacheJson(CACHE_KEY_LATEST_TASK, payload);
        return withMeta(payload, "mysql");
    }

    @Override
    public Map<String, Object> getLatestSummary() {
        Object cached = redisUtil.get(CACHE_KEY_LATEST_SUMMARY);
        Map<String, Object> cachedSummary = parseMapCache(cached);
        if (cachedSummary != null) {
            return withMeta(cachedSummary, "redis");
        }

        AnalyticsKmeansTask task = findLatestSuccessfulTask();
        if (task == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", buildTaskPayload(task));
        payload.put("summary", safeMap(task.getResultSummary()));
        payload.put("llmOverview", safeMap(task.getLlmOverview()));
        payload.put("segmentCount", task.getClusterCount());
        payload.put("featureColumns", task.getFeatureColumns() == null
                ? Collections.emptyList() : task.getFeatureColumns());
        payload.put("cacheStrategy", CACHE_STRATEGY);

        cacheJson(CACHE_KEY_LATEST_SUMMARY, payload);
        return withMeta(payload, "mysql");
    }

    @Override
    public Map<String, Object> getLatestSegments() {
        Object cached = redisUtil.get(CACHE_KEY_LATEST_SEGMENTS);
        Map<String, Object> cachedPayload = parseMapCache(cached);
        if (cachedPayload != null) {
            return withMeta(cachedPayload, "redis");
        }

        AnalyticsKmeansTask task = findLatestSuccessfulTask();
        if (task == null) {
            return Collections.emptyMap();
        }

        List<AnalyticsKmeansSegment> segments = segmentMapper.selectList(
                new LambdaQueryWrapper<AnalyticsKmeansSegment>()
                        .eq(AnalyticsKmeansSegment::getTaskId, task.getId())
                        .orderByDesc(AnalyticsKmeansSegment::getUserCount)
                        .orderByAsc(AnalyticsKmeansSegment::getSegmentCode));

        List<Map<String, Object>> payload = new ArrayList<>();
        for (AnalyticsKmeansSegment segment : segments) {
            payload.add(buildSegmentPayload(segment));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", buildTaskPayload(task));
        result.put("records", payload);
        result.put("segmentCount", payload.size());
        result.put("cacheStrategy", CACHE_STRATEGY);

        cacheJson(CACHE_KEY_LATEST_SEGMENTS, result);
        return withMeta(result, "mysql");
    }

    @Override
    public Map<String, Object> getSegmentUsers(String segmentCode, int page, int size) {
        AnalyticsKmeansTask task = findLatestSuccessfulTask();
        if (task == null) {
            return Collections.emptyMap();
        }

        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Page<Map<String, Object>> pager = new Page<>(safePage, safeSize);
        IPage<Map<String, Object>> result = userResultMapper.selectSegmentUserPage(
                pager, task.getId(), normalizeSegmentCode(segmentCode));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", buildTaskPayload(task));
        payload.put("segmentCode", normalizeSegmentCode(segmentCode));
        payload.put("records", result.getRecords());
        payload.put("total", result.getTotal());
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("size", result.getSize());
        payload.put("cacheStrategy", CACHE_STRATEGY);
        payload.put("dataSource", "mysql");
        payload.put("storageBoundary", buildStorageBoundary());
        payload.put("freshness", buildFreshness(payload, "mysql"));
        return payload;
    }

    @Override
    public Map<String, Object> getUserClusterDetail(Long userId) {
        AnalyticsKmeansTask task = findLatestSuccessfulTask();
        if (task == null || userId == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> detail = userResultMapper.selectUserClusterDetail(task.getId(), userId);
        if (detail == null || detail.isEmpty()) {
            return Collections.emptyMap();
        }

        Object segmentCodeObj = detail.get("segmentCode");
        if (segmentCodeObj != null) {
            AnalyticsKmeansSegment segment = segmentMapper.selectOne(
                    new LambdaQueryWrapper<AnalyticsKmeansSegment>()
                            .eq(AnalyticsKmeansSegment::getTaskId, task.getId())
                            .eq(AnalyticsKmeansSegment::getSegmentCode, segmentCodeObj.toString())
                            .last("LIMIT 1"));
            if (segment != null) {
                detail.put("segment", buildSegmentPayload(segment));
            }
        }

        detail.put("task", buildTaskPayload(task));
        detail.put("cacheStrategy", CACHE_STRATEGY);
        detail.put("dataSource", "mysql");
        detail.put("storageBoundary", buildStorageBoundary());
        detail.put("freshness", buildFreshness(detail, "mysql"));
        return detail;
    }

    @Override
    public Map<String, Object> getTaskHistory(int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Page<AnalyticsJobLog> pager = new Page<>(safePage, safeSize);
        IPage<AnalyticsJobLog> logPage = jobLogMapper.selectPage(
                pager,
                new LambdaQueryWrapper<AnalyticsJobLog>()
                        .eq(AnalyticsJobLog::getJobName, JOB_NAME_KMEANS)
                        .orderByDesc(AnalyticsJobLog::getStartTime)
                        .orderByDesc(AnalyticsJobLog::getId)
        );

        List<AnalyticsJobLog> jobLogs = logPage.getRecords();
        List<String> batchNos = jobLogs.stream()
                .map(AnalyticsJobLog::getBatchNo)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        Map<String, AnalyticsKmeansTask> taskByBatchNo = new LinkedHashMap<>();
        if (!batchNos.isEmpty()) {
            List<AnalyticsKmeansTask> tasks = taskMapper.selectList(
                    new LambdaQueryWrapper<AnalyticsKmeansTask>()
                            .in(AnalyticsKmeansTask::getBatchNo, batchNos)
            );
            for (AnalyticsKmeansTask task : tasks) {
                taskByBatchNo.put(task.getBatchNo(), task);
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (AnalyticsJobLog jobLog : jobLogs) {
            records.add(buildHistoryRecord(jobLog, taskByBatchNo.get(jobLog.getBatchNo())));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("records", records);
        payload.put("total", logPage.getTotal());
        payload.put("current", logPage.getCurrent());
        payload.put("pages", logPage.getPages());
        payload.put("size", logPage.getSize());
        payload.put("runtime", buildRunnerRuntime());
        payload.put("cacheStrategy", "mysql_history_realtime_runner_state");
        payload.put("dataSource", "mysql");
        payload.put("storageBoundary", buildStorageBoundary());
        payload.put("freshness", buildHistoryFreshness(records));
        return payload;
    }

    @Override
    public Map<String, Object> triggerTask(AnalyticsKmeansTriggerDTO request) {
        LocalDate snapshotDate = normalizeSnapshotDate(request == null ? null : request.getSnapshotDate());
        int requestedK = request != null && request.getK() != null ? request.getK() : DEFAULT_K;
        boolean autoSelectK = request != null && Boolean.TRUE.equals(request.getAutoK());
        int minK = request != null && request.getMinK() != null ? request.getMinK() : DEFAULT_MIN_K;
        int maxK = request != null && request.getMaxK() != null ? request.getMaxK() : DEFAULT_MAX_K;

        if (minK > maxK) {
            throw new BusinessException(400, "最小聚类数量不能大于最大聚类数量");
        }
        if (autoSelectK && (requestedK < minK || requestedK > maxK)) {
            requestedK = minK;
        }

        if (request != null && (request.getK() != null
                || request.getAutoK() != null
                || request.getMinK() != null
                || request.getMaxK() != null)) {
            log.info("K-means manual trigger request received: k={}, autoK={}, minK={}, maxK={}",
                    requestedK, autoSelectK, minK, maxK);
        }

        if (requestedK <= 0) {
            throw new BusinessException(400, "k must be greater than 0");
        }
        if (minK <= 0 || maxK <= 0) {
            throw new BusinessException(400, "minK and maxK must be greater than 0");
        }
        if (minK < 2) {
            minK = 2;
        }
        if (maxK < minK) {
            throw new BusinessException(400, "minK must be less than or equal to maxK");
        }
        if (requestedK < minK || requestedK > maxK) {
            throw new BusinessException(400, "k must be within [minK, maxK]");
        }

        if (manualLaunchInProgress.get() || countRunningJobsInDatabase() > 0) {
            throw new BusinessException(409, "当前已有分群任务在运行，请稍后再试");
        }

        assertTrainingDataReady();

        Path scriptPath = resolveScriptPath();
        if (scriptPath == null || !Files.exists(scriptPath)) {
            throw new BusinessException(500, "未找到分群执行脚本，请检查 scripts/run-kmeans-user-clustering.ps1");
        }
        if (!manualLaunchInProgress.compareAndSet(false, true)) {
            throw new BusinessException(409, "当前已有分群任务在运行，请稍后再试");
        }

        String launchId = "manual_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        KmeansLaunchCommand command = new KmeansLaunchCommand(
                launchId, snapshotDate, requestedK, autoSelectK, minK, maxK, scriptPath
        );
        log.info("K-means manual trigger accepted: launchId={}, snapshotDate={}, k={}, autoK={}, minK={}, maxK={}",
                launchId, snapshotDate, requestedK, autoSelectK, minK, maxK);
        Map<String, Object> acceptedPayload = buildAcceptedTriggerPayload(command);
        lastManualLaunch = acceptedPayload;

        try {
            CompletableFuture.runAsync(() -> executeManualTrigger(command));
        } catch (Exception ex) {
            manualLaunchInProgress.set(false);
            throw new BusinessException(500, "分群任务启动失败: " + ex.getMessage());
        }
        managementWorkbenchRealtimeService.notifyAdminAnalysisRefresh(
                "kmeans-task-accepted",
                new LinkedHashMap<>(acceptedPayload)
        );
        return acceptedPayload;
    }

    private AnalyticsKmeansTask findLatestSuccessfulTask() {
        return taskMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsKmeansTask>()
                        .eq(AnalyticsKmeansTask::getStatus, TASK_STATUS_SUCCESS)
                        .orderByDesc(AnalyticsKmeansTask::getSnapshotDate)
                        .orderByDesc(AnalyticsKmeansTask::getId)
                        .last("LIMIT 1"));
    }

    private Map<String, Object> buildTaskPayload(AnalyticsKmeansTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", task.getId());
        payload.put("batchNo", task.getBatchNo());
        payload.put("snapshotDate", task.getSnapshotDate());
        payload.put("status", task.getStatus());
        payload.put("algorithmName", task.getAlgorithmName());
        payload.put("modelVersion", task.getModelVersion());
        payload.put("featureVersion", task.getFeatureVersion());
        payload.put("clusterCount", task.getClusterCount());
        payload.put("sampleUserCount", task.getSampleUserCount());
        payload.put("clusteredUserCount", task.getClusteredUserCount());
        payload.put("coldStartUserCount", task.getColdStartUserCount());
        payload.put("silhouetteScore", task.getSilhouetteScore());
        payload.put("inertiaScore", task.getInertiaScore());
        payload.put("featureColumns", task.getFeatureColumns() == null
                ? Collections.emptyList() : task.getFeatureColumns());
        payload.put("startTime", task.getStartTime());
        payload.put("endTime", task.getEndTime());
        return payload;
    }

    private Map<String, Object> buildSegmentPayload(AnalyticsKmeansSegment segment) {
        String profile = resolveSegmentProfile(segment);
        Map<String, Object> activationPlan = buildActivationPlan(segment);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", segment.getId());
        payload.put("taskId", segment.getTaskId());
        payload.put("snapshotDate", segment.getSnapshotDate());
        payload.put("segmentCode", segment.getSegmentCode());
        payload.put("segmentName", segment.getSegmentName());
        payload.put("segmentDescription", segment.getSegmentDescription());
        payload.put("llmSummary", segment.getLlmSummary());
        payload.put("operationSuggestion", segment.getOperationSuggestion());
        payload.put("userCount", segment.getUserCount());
        payload.put("percentage", segment.getPercentage());
        payload.put("avgOrderCount90d", segment.getAvgOrderCount90d());
        payload.put("avgOrderAmount90d", segment.getAvgOrderAmount90d());
        payload.put("avgBehaviorCount30d", segment.getAvgBehaviorCount30d());
        payload.put("avgActiveDays30d", segment.getAvgActiveDays30d());
        payload.put("avgRecencyDays", segment.getAvgRecencyDays());
        payload.put("avgPricePerOrder", segment.getAvgPricePerOrder());
        payload.put("featureCenter", safeMap(segment.getFeatureCenter()));
        payload.put("topCategories", segment.getTopCategories() == null
                ? Collections.emptyList() : segment.getTopCategories());
        payload.put("topTags", segment.getTopTags() == null
                ? Collections.emptyList() : segment.getTopTags());
        payload.put("strategyArchetype", profile);
        payload.put("playbookVersion", "kmeans-growth-playbook-v2");
        payload.put("activationPlan", activationPlan);
        payload.put("recommendationPolicy", buildSegmentRecommendationPolicy(profile, segment));
        return payload;
    }

    private Map<String, Object> buildActivationPlan(AnalyticsKmeansSegment segment) {
        String profile = resolveSegmentProfile(segment);
        String categoryText = joinTopValues(segment.getTopCategories(), "核心品类");
        String tagText = joinTopValues(segment.getTopTags(), "站内兴趣标签");
        Map<String, Object> plan = new LinkedHashMap<>();
        List<Map<String, Object>> actions = new ArrayList<>();
        String summary;
        String priority;

        if ("sleeping".equals(profile)) {
            summary = "近期活跃度下降，适合用唤醒型策略重新召回，先拉回访问再追求转化。";
            priority = "high";
            actions.add(buildActivationAction("recommendation", "推荐策略",
                    "在首页和消息链路优先展示用户曾关注的" + categoryText + "回流专题、爆款和降价商品。",
                    "回流访问率 / 二次浏览率"));
            actions.add(buildActivationAction("coupon", "优惠券策略",
                    "发放限时唤醒券、无门槛券或小额门槛券，控制成本的同时提升回流概率。",
                    "券领取率 / 唤醒转化率"));
            actions.add(buildActivationAction("message", "消息触达策略",
                    "用站内信或订阅消息推送限时活动、补货提醒和折扣信息，文案突出紧迫感。",
                    "触达打开率 / 唤醒下单率"));
        } else if ("high_value".equals(profile)) {
            summary = "客单价和消费频次较高，适合利润优先和会员权益型运营，不建议大额直降。";
            priority = "high";
            actions.add(buildActivationAction("recommendation", "推荐策略",
                    "优先推荐" + categoryText + "的高客单新品、套装和升级款，突出品质和会员专享。",
                    "高客单点击率 / 连带购买率"));
            actions.add(buildActivationAction("coupon", "优惠券策略",
                    "以会员复购券、满减券和加价购权益为主，避免直接使用深度折扣破坏价值感。",
                    "复购率 / 客单价"));
            actions.add(buildActivationAction("message", "消息触达策略",
                    "针对" + tagText + "推送新品首发、会员日和专属服务提醒，保持高价值用户黏性。",
                    "会员活跃率 / 高价值复购率"));
        } else if ("active_interest".equals(profile)) {
            summary = "浏览和加购意愿强，但成交还不够，适合用内容承接和转化刺激把兴趣变成订单。";
            priority = "high";
            actions.add(buildActivationAction("recommendation", "推荐策略",
                    "在推荐位连续曝光" + categoryText + "相似商品、对比款和热卖榜，缩短决策路径。",
                    "点击率 / 加购率"));
            actions.add(buildActivationAction("coupon", "优惠券策略",
                    "发放首单券、加购转化券或购物车专享券，重点刺激最后一步成交。",
                    "加购转化率 / 首单率"));
            actions.add(buildActivationAction("message", "消息触达策略",
                    "对浏览未购和加购未买用户发送限时提醒、购物车召回消息和同类商品对比信息。",
                    "召回访问率 / 成交转化率"));
        } else if ("price_sensitive".equals(profile)) {
            summary = "对价格敏感，适合强调性价比、促销节点和价格变动提醒。";
            priority = "medium";
            actions.add(buildActivationAction("recommendation", "推荐策略",
                    "主推" + categoryText + "中的高性价比商品、折扣专区和凑单商品。",
                    "价格敏感用户点击率"));
            actions.add(buildActivationAction("coupon", "优惠券策略",
                    "配置阶梯满减券、组合券和限时折扣券，让用户更容易完成凑单。",
                    "用券率 / 凑单成功率"));
            actions.add(buildActivationAction("message", "消息触达策略",
                    "推送降价提醒、活动倒计时和限时拼团信息，强化价格优势认知。",
                    "降价提醒打开率 / 下单率"));
        } else if ("loyal".equals(profile)) {
            summary = "活跃和复购表现较稳定，可以重点经营长期价值和连带销售。";
            priority = "medium";
            actions.add(buildActivationAction("recommendation", "推荐策略",
                    "围绕" + categoryText + "做复购商品、搭配购和补货推荐，突出长期消费场景。",
                    "复购点击率 / 连带率"));
            actions.add(buildActivationAction("coupon", "优惠券策略",
                    "发放周期复购券、会员成长权益和老客专属券，提升长期留存。",
                    "老客复购率 / 留存率"));
            actions.add(buildActivationAction("message", "消息触达策略",
                    "按周期发送补货提醒、老客福利和新品抢先购消息，维持品牌心智。",
                    "周期回购率 / 触达活跃率"));
        } else {
            summary = "分群整体表现较均衡，适合用通用推荐、轻促销和日常触达逐步放大价值。";
            priority = "medium";
            actions.add(buildActivationAction("recommendation", "推荐策略",
                    "优先推荐" + categoryText + "的站内热门商品、场景合集和同品类热卖榜。",
                    "推荐点击率"));
            actions.add(buildActivationAction("coupon", "优惠券策略",
                    "采用轻量满减券或品类券做转化提速，避免对整体利润造成较大压力。",
                    "用券转化率"));
            actions.add(buildActivationAction("message", "消息触达策略",
                    "结合" + tagText + "做周常活动提醒和内容种草消息，逐步提升活跃和转化。",
                    "消息打开率 / 周活跃率"));
        }

        plan.put("profile", profile);
        plan.put("priority", priority);
        plan.put("summary", summary);
        plan.put("actions", actions);
        return plan;
    }

    private Map<String, Object> buildSegmentRecommendationPolicy(String profile, AnalyticsKmeansSegment segment) {
        Map<String, Object> policy = new LinkedHashMap<>();
        double exploreRate;
        String objective;
        List<String> guardrails = new ArrayList<>();
        if ("sleeping".equals(profile)) {
            exploreRate = 0.32D;
            objective = "先拉回流量，再承接转化";
            guardrails.add("回流专题覆盖率 >= 80%");
            guardrails.add("唤醒券成本占 GMV 比例 <= 8%");
        } else if ("high_value".equals(profile)) {
            exploreRate = 0.12D;
            objective = "利润与复购优先";
            guardrails.add("高客单曝光占比 >= 60%");
            guardrails.add("深折扣商品曝光占比 <= 20%");
        } else if ("active_interest".equals(profile)) {
            exploreRate = 0.2D;
            objective = "缩短决策链路，提升转化效率";
            guardrails.add("同类对比商品至少 2 个");
            guardrails.add("加购召回链路触达率 >= 70%");
        } else if ("price_sensitive".equals(profile)) {
            exploreRate = 0.25D;
            objective = "承接价格敏感需求，扩大性价比成交";
            guardrails.add("中低价位商品曝光占比 >= 65%");
            guardrails.add("价格带覆盖至少 3 档");
        } else if ("loyal".equals(profile)) {
            exploreRate = 0.15D;
            objective = "稳定复购与连带购买";
            guardrails.add("复购型商品曝光占比 >= 50%");
            guardrails.add("会员权益触达率 >= 70%");
        } else {
            exploreRate = 0.2D;
            objective = "均衡探索与转化，逐步放大用户价值";
            guardrails.add("类目集中度 Top1 <= 55%");
            guardrails.add("单商家曝光占比 <= 35%");
        }

        policy.put("objective", objective);
        policy.put("exploreRate", Math.round(exploreRate * 10000D) / 100D);
        policy.put("guardrails", guardrails);
        policy.put("topCategories", segment.getTopCategories() == null
                ? Collections.emptyList()
                : segment.getTopCategories().stream().limit(3).collect(Collectors.toList()));
        policy.put("topTags", segment.getTopTags() == null
                ? Collections.emptyList()
                : segment.getTopTags().stream().limit(3).collect(Collectors.toList()));
        return policy;
    }

    private Map<String, Object> buildActivationAction(String type, String title, String description, String metric) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", type);
        action.put("title", title);
        action.put("description", description);
        action.put("metric", metric);
        return action;
    }

    private String resolveSegmentProfile(AnalyticsKmeansSegment segment) {
        return AnalyticsKmeansSegmentProfileUtil.resolveProfile(segment);
    }

    private Map<String, Object> buildHistoryRecord(AnalyticsJobLog jobLog, AnalyticsKmeansTask task) {
        Map<String, Object> resultSummary = safeMap(jobLog.getResultSummary());
        Map<String, Object> record = new LinkedHashMap<>();
        LocalDateTime startTime = task != null && task.getStartTime() != null ? task.getStartTime() : jobLog.getStartTime();
        LocalDateTime endTime = task != null && task.getEndTime() != null ? task.getEndTime() : jobLog.getEndTime();

        record.put("jobLogId", jobLog.getId());
        record.put("taskId", task == null ? null : task.getId());
        record.put("batchNo", jobLog.getBatchNo());
        record.put("snapshotDate", task != null && task.getSnapshotDate() != null ? task.getSnapshotDate() : jobLog.getSnapshotDate());
        record.put("jobType", jobLog.getJobType());
        record.put("jobStatus", jobLog.getStatus());
        record.put("taskStatus", task == null ? null : task.getStatus());
        record.put("finalStatus", firstNonBlank(task == null ? null : task.getStatus(), jobLog.getStatus()));
        record.put("requestedClusterCount", firstNonNull(
                getIntegerValue(resultSummary.get("requestedClusterCount")),
                task == null ? null : task.getClusterCount()));
        record.put("actualClusterCount", firstNonNull(
                task == null ? null : task.getClusterCount(),
                getIntegerValue(resultSummary.get("actualClusterCount"))));
        record.put("sampleUserCount", firstNonNull(
                task == null ? null : task.getSampleUserCount(),
                getLongValue(resultSummary.get("sampleUserCount")),
                jobLog.getProcessedCount()));
        record.put("clusteredUserCount", firstNonNull(
                task == null ? null : task.getClusteredUserCount(),
                getLongValue(resultSummary.get("clusteredUserCount")),
                jobLog.getOutputCount()));
        record.put("coldStartUserCount", firstNonNull(
                task == null ? null : task.getColdStartUserCount(),
                getLongValue(resultSummary.get("coldStartUserCount"))));
        record.put("bestSegmentCode", firstNonBlank(
                getStringValue(resultSummary.get("bestSegmentCode")),
                readBestSegmentFromTask(task)));
        record.put("silhouetteScore", task == null ? null : task.getSilhouetteScore());
        record.put("inertiaScore", task == null ? null : task.getInertiaScore());
        record.put("modelVersion", task == null ? null : task.getModelVersion());
        record.put("featureVersion", task == null ? null : task.getFeatureVersion());
        record.put("startTime", startTime);
        record.put("endTime", endTime);
        record.put("durationSeconds", calculateDurationSeconds(startTime, endTime));
        record.put("errorMessage", firstNonBlank(task == null ? null : task.getErrorMessage(), jobLog.getErrorMessage()));
        record.put("dataSource", "mysql");
        return record;
    }

    private Map<String, Object> buildRunnerRuntime() {
        Path scriptPath = resolveScriptPath();
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("triggerInProgress", manualLaunchInProgress.get());
        runtime.put("runningInDatabase", countRunningJobsInDatabase() > 0);
        runtime.put("runnerShell", runnerShell);
        runtime.put("scriptPath", scriptPath == null ? null : scriptPath.toString());
        runtime.put("scriptReady", scriptPath != null && Files.exists(scriptPath));
        runtime.put("lastManualLaunch", lastManualLaunch == null ? Collections.emptyMap() : lastManualLaunch);
        runtime.put("storageBoundary", buildStorageBoundary());
        return runtime;
    }

    private Map<String, Object> buildAcceptedTriggerPayload(KmeansLaunchCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("launchId", command.launchId);
        payload.put("status", "accepted");
        payload.put("triggeredAt", LocalDateTime.now());
        payload.put("snapshotDate", command.snapshotDate);
        payload.put("k", command.requestedK);
        payload.put("autoK", command.autoSelectK);
        payload.put("minK", command.minK);
        payload.put("maxK", command.maxK);
        payload.put("shellCommand", runnerShell);
        payload.put("scriptPath", command.scriptPath.toString());
        payload.put("commandPreview", buildCommandPreview(buildProcessCommand(command)));
        return payload;
    }

    private void executeManualTrigger(KmeansLaunchCommand command) {
        List<String> processCommand = buildProcessCommand(command);
        Map<String, Object> runningState = new LinkedHashMap<>(lastManualLaunch);
        runningState.put("status", "running");
        runningState.put("startedAt", LocalDateTime.now());
        lastManualLaunch = runningState;
        managementWorkbenchRealtimeService.notifyAdminAnalysisRefresh(
                "kmeans-task-running",
                new LinkedHashMap<>(runningState)
        );

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(processCommand);
            processBuilder.redirectErrorStream(true);
            processBuilder.directory(command.scriptPath.getParent().toFile());

            log.info("Starting K-means task manually: launchId={}, command={}",
                    command.launchId, buildCommandPreview(processCommand));

            Process process = processBuilder.start();
            drainProcessOutput(process, command.launchId);
            int exitCode = process.waitFor();

            Map<String, Object> finishState = new LinkedHashMap<>(lastManualLaunch);
            finishState.put("status", exitCode == 0 ? "success" : "failed");
            finishState.put("exitCode", exitCode);
            finishState.put("finishedAt", LocalDateTime.now());
            lastManualLaunch = finishState;
            managementWorkbenchRealtimeService.notifyAdminAnalysisRefresh(
                    "kmeans-task-finished",
                    new LinkedHashMap<>(finishState)
            );

            if (exitCode == 0) {
                log.info("K-means task finished successfully: launchId={}", command.launchId);
            } else {
                log.warn("K-means task finished with non-zero exit code: launchId={}, exitCode={}",
                        command.launchId, exitCode);
            }
        } catch (Exception ex) {
            Map<String, Object> failedState = new LinkedHashMap<>(lastManualLaunch);
            failedState.put("status", "failed");
            failedState.put("finishedAt", LocalDateTime.now());
            failedState.put("errorMessage", ex.getMessage());
            lastManualLaunch = failedState;
            managementWorkbenchRealtimeService.notifyAdminAnalysisRefresh(
                    "kmeans-task-finished",
                    new LinkedHashMap<>(failedState)
            );
            log.error("Manual K-means task execution failed: launchId={}, message={}",
                    command.launchId, ex.getMessage(), ex);
        } finally {
            manualLaunchInProgress.set(false);
        }
    }

    private void drainProcessOutput(Process process, String launchId) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[kmeans:{}] {}", launchId, line);
            }
        } finally {
            reader.close();
        }
    }

    private List<String> buildProcessCommand(KmeansLaunchCommand command) {
        List<String> args = new ArrayList<>();
        args.add(runnerShell);
        args.add("-ExecutionPolicy");
        args.add("Bypass");
        args.add("-File");
        args.add(command.scriptPath.toString());
        args.add("-SnapshotDate");
        args.add(command.snapshotDate.toString());
        args.add("-K");
        args.add(String.valueOf(command.requestedK));
        args.add("-AutoK");
        args.add(String.valueOf(command.autoSelectK));
        args.add("-MinK");
        args.add(String.valueOf(command.minK));
        args.add("-MaxK");
        args.add(String.valueOf(command.maxK));
        return args;
    }

    private Path resolveScriptPath() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.hasText(runnerScriptPath)) {
            candidates.add(resolvePathCandidate(runnerScriptPath));
        }

        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        candidates.add(userDir.resolve("../scripts/run-kmeans-user-clustering.ps1").normalize());
        candidates.add(userDir.resolve("scripts/run-kmeans-user-clustering.ps1").normalize());
        if (userDir.getParent() != null) {
            candidates.add(userDir.getParent().resolve("scripts/run-kmeans-user-clustering.ps1").normalize());
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private Path resolvePathCandidate(String rawPath) {
        Path direct = Paths.get(rawPath);
        if (direct.isAbsolute()) {
            return direct.normalize();
        }
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        return userDir.resolve(rawPath).normalize();
    }

    private int countRunningJobsInDatabase() {
        Long runningCount = jobLogMapper.selectCount(
                new LambdaQueryWrapper<AnalyticsJobLog>()
                        .eq(AnalyticsJobLog::getJobName, JOB_NAME_KMEANS)
                        .eq(AnalyticsJobLog::getStatus, TASK_STATUS_RUNNING)
        );
        return runningCount == null ? 0 : runningCount.intValue();
    }

    private void assertTrainingDataReady() {
        long orderCount = safeCount(orderMapper.selectCount(null));
        long behaviorCount = safeCount(userBehaviorMapper.selectCount(null));
        if (orderCount > 0 && behaviorCount > 0) {
            return;
        }

        AnalyticsKmeansTask latestTask = findLatestSuccessfulTask();
        if (latestTask != null) {
            long segmentCount = safeCount(segmentMapper.selectCount(
                    new LambdaQueryWrapper<AnalyticsKmeansSegment>()
                            .eq(AnalyticsKmeansSegment::getTaskId, latestTask.getId())
            ));
            long userResultCount = safeCount(userResultMapper.selectCount(
                    new LambdaQueryWrapper<AnalyticsKmeansUserResult>()
                            .eq(AnalyticsKmeansUserResult::getTaskId, latestTask.getId())
            ));
            if (segmentCount > 0 && userResultCount > 0) {
                log.warn("K-means trigger uses existing artifact fallback because source samples are insufficient: orderCount={}, behaviorCount={}, taskId={}, segmentCount={}, userResultCount={}",
                        orderCount, behaviorCount, latestTask.getId(), segmentCount, userResultCount);
                return;
            }
        }

        throw new BusinessException(409,
                String.format("当前库缺少分群训练样本：订单数=%d，行为数=%d。请先导入完整 seed.sql 数据后再执行分群任务。", orderCount, behaviorCount));
    }

    private long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    private LocalDate normalizeSnapshotDate(String snapshotDate) {
        if (!StringUtils.hasText(snapshotDate)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(snapshotDate.trim());
        } catch (Exception ex) {
            throw new BusinessException(400, "快照日期格式错误，请使用 yyyy-MM-dd");
        }
    }

    private String normalizeSegmentCode(String segmentCode) {
        if (segmentCode == null) {
            return null;
        }
        String trimmed = segmentCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String joinTopValues(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .limit(2)
                .collect(Collectors.joining("、"));
    }

    private BigDecimal safeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long calculateDurationSeconds(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) {
            return null;
        }
        LocalDateTime finalEndTime = endTime == null ? LocalDateTime.now() : endTime;
        return ChronoUnit.SECONDS.between(startTime, finalEndTime);
    }

    private String buildCommandPreview(List<String> command) {
        return command.stream()
                .map(this::quoteIfNeeded)
                .collect(Collectors.joining(" "));
    }

    private String quoteIfNeeded(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(" ") ? "\"" + value + "\"" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
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

    private Integer getIntegerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            log.debug("[Cluster] Integer解析失败: {}", value);
            return null;
        }
    }

    private Long getLongValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            log.debug("[Cluster] Long解析失败: {}", value);
            return null;
        }
    }

    private String getStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String readBestSegmentFromTask(AnalyticsKmeansTask task) {
        if (task == null || task.getResultSummary() == null) {
            return null;
        }
        Object bestSegmentCode = task.getResultSummary().get("bestSegmentCode");
        return bestSegmentCode == null ? null : String.valueOf(bestSegmentCode);
    }

    private Map<String, Object> safeMap(Map<String, Object> source) {
        return source == null ? Collections.emptyMap() : source;
    }

    private void cacheJson(String key, Object value) {
        redisUtil.set(key, JSON.toJSONString(value), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private Map<String, Object> withMeta(Map<String, Object> payload, String dataSource) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (payload != null) {
            result.putAll(payload);
        }
        result.put("dataSource", dataSource);
        result.put("storageBoundary", buildStorageBoundary());
        result.put("freshness", buildFreshness(result, dataSource));
        return result;
    }

    private Map<String, Object> buildStorageBoundary() {
        Map<String, Object> storageBoundary = new LinkedHashMap<>();
        storageBoundary.put("mysql", Arrays.asList(
                "analytics_job_log",
                "analytics_kmeans_task",
                "analytics_kmeans_segment",
                "analytics_kmeans_user_result",
                "analytics_kmeans_feature_snapshot"
        ));
        storageBoundary.put("redis", Arrays.asList(
                CACHE_KEY_LATEST_TASK,
                CACHE_KEY_LATEST_SUMMARY,
                CACHE_KEY_LATEST_SEGMENTS
        ));
        storageBoundary.put("rule", STORAGE_RULE);
        return storageBoundary;
    }

    private Map<String, Object> buildFreshness(Map<String, Object> payload, String dataSource) {
        Map<String, Object> taskMap = extractTaskMap(payload);
        Map<String, Object> freshness = new LinkedHashMap<>();
        freshness.put("snapshotDate", taskMap.get("snapshotDate"));
        freshness.put("lastTaskTime", firstNonNull(taskMap.get("endTime"), taskMap.get("startTime")));
        freshness.put("readTime", LocalDateTime.now());
        freshness.put("dataSource", dataSource);
        freshness.put("cacheStrategy", payload.get("cacheStrategy"));
        freshness.put("storageRule", STORAGE_RULE);
        return freshness;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTaskMap(Map<String, Object> payload) {
        Object taskObj = payload.get("task");
        if (taskObj instanceof Map) {
            return (Map<String, Object>) taskObj;
        }
        return payload == null ? Collections.emptyMap() : payload;
    }

    private Map<String, Object> buildHistoryFreshness(List<Map<String, Object>> records) {
        Map<String, Object> freshness = new LinkedHashMap<>();
        Map<String, Object> firstRecord = records.isEmpty() ? Collections.emptyMap() : records.get(0);
        freshness.put("snapshotDate", firstRecord.get("snapshotDate"));
        freshness.put("lastTaskTime", firstNonNull(firstRecord.get("endTime"), firstRecord.get("startTime")));
        freshness.put("readTime", LocalDateTime.now());
        freshness.put("dataSource", "mysql");
        freshness.put("cacheStrategy", "mysql_history_realtime_runner_state");
        freshness.put("storageRule", STORAGE_RULE);
        return freshness;
    }

    private int normalizePage(int page) {
        return page <= 0 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMapCache(Object cached) {
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        if (!(cached instanceof String)) {
            return null;
        }
        String text = ((String) cached).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("[Cluster] JSON解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static class KmeansLaunchCommand {

        private final String launchId;
        private final LocalDate snapshotDate;
        private final int requestedK;
        private final boolean autoSelectK;
        private final int minK;
        private final int maxK;
        private final Path scriptPath;

        private KmeansLaunchCommand(String launchId,
                                    LocalDate snapshotDate,
                                    int requestedK,
                                    boolean autoSelectK,
                                    int minK,
                                    int maxK,
                                    Path scriptPath) {
            this.launchId = launchId;
            this.snapshotDate = snapshotDate;
            this.requestedK = requestedK;
            this.autoSelectK = autoSelectK;
            this.minK = minK;
            this.maxK = maxK;
            this.scriptPath = scriptPath;
        }
    }

}
