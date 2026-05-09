package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.AnalyticsKmeansSegment;
import com.ecommerce.entity.AnalyticsKmeansTask;
import com.ecommerce.entity.AnalyticsKmeansUserResult;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.AnalyticsKmeansSegmentMapper;
import com.ecommerce.mapper.AnalyticsKmeansTaskMapper;
import com.ecommerce.mapper.AnalyticsKmeansUserResultMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KmeansCoverageBackfillService {

    private static final Logger log = LoggerFactory.getLogger(KmeansCoverageBackfillService.class);
    private static final String TASK_STATUS_SUCCESS = "success";
    private static final String DEFAULT_SEGMENT_CODE = "COLD_START";
    private static final String DEFAULT_SEGMENT_NAME = "冷启动回补";

    private final AnalyticsKmeansTaskMapper taskMapper;
    private final AnalyticsKmeansSegmentMapper segmentMapper;
    private final AnalyticsKmeansUserResultMapper userResultMapper;
    private final UserMapper userMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final OrderMapper orderMapper;

    @Value("${analytics.python.backfill.lookback-days:30}")
    private int lookbackDays;

    @Value("${analytics.python.backfill.max-users-per-run:5000}")
    private int maxUsersPerRun;

    public KmeansCoverageBackfillService(AnalyticsKmeansTaskMapper taskMapper,
                                         AnalyticsKmeansSegmentMapper segmentMapper,
                                         AnalyticsKmeansUserResultMapper userResultMapper,
                                         UserMapper userMapper,
                                         UserBehaviorMapper userBehaviorMapper,
                                         OrderMapper orderMapper) {
        this.taskMapper = taskMapper;
        this.segmentMapper = segmentMapper;
        this.userResultMapper = userResultMapper;
        this.userMapper = userMapper;
        this.userBehaviorMapper = userBehaviorMapper;
        this.orderMapper = orderMapper;
    }

    public Map<String, Object> backfillLatestTaskMissingUsers() {
        AnalyticsKmeansTask latestTask = findLatestSuccessfulTask();
        if (latestTask == null || latestTask.getId() == null) {
            return buildResult("skipped", 0, 0, "未找到可回补的成功分群任务");
        }

        int safeLookbackDays = Math.max(7, Math.min(lookbackDays, 180));
        int safeMaxUsersPerRun = Math.max(100, Math.min(maxUsersPerRun, 20000));
        LocalDateTime lookbackStart = LocalDateTime.now().minusDays(safeLookbackDays);

        Set<Long> candidateUserIds = loadCandidateUserIds(lookbackStart, safeMaxUsersPerRun);
        if (candidateUserIds.isEmpty()) {
            return buildResult("skipped", 0, 0, "未找到需要回补的活跃用户");
        }

        Set<Long> assignedUserIds = userResultMapper.selectList(
                        new LambdaQueryWrapper<AnalyticsKmeansUserResult>()
                                .eq(AnalyticsKmeansUserResult::getTaskId, latestTask.getId())
                                .select(AnalyticsKmeansUserResult::getUserId))
                .stream()
                .map(AnalyticsKmeansUserResult::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        List<Long> missingUserIds = candidateUserIds.stream()
                .filter(userId -> !assignedUserIds.contains(userId))
                .sorted()
                .collect(Collectors.toList());
        if (missingUserIds.isEmpty()) {
            return buildResult("ok", candidateUserIds.size(), 0, "本轮无缺失用户");
        }

        AnalyticsKmeansSegment fallbackSegment = resolveFallbackSegment(latestTask);
        int nextSortOrder = resolveNextSortOrder(latestTask.getId());
        int insertedCount = 0;
        for (Long userId : missingUserIds) {
            AnalyticsKmeansUserResult row = new AnalyticsKmeansUserResult();
            row.setTaskId(latestTask.getId());
            row.setSnapshotDate(latestTask.getSnapshotDate());
            row.setUserId(userId);
            row.setSegmentCode(fallbackSegment.getSegmentCode());
            row.setSegmentName(fallbackSegment.getSegmentName());
            row.setClusterIndex(null);
            row.setDistanceToCenter(null);
            row.setConfidenceScore(new BigDecimal("0.10"));
            row.setIsColdStart(1);
            row.setSortOrder(nextSortOrder++);
            row.setPersonaSummary("自动回补: 保证活跃用户分群覆盖");
            userResultMapper.insert(row);
            insertedCount++;
        }

        touchTaskAndSegmentMetrics(latestTask, fallbackSegment, insertedCount);

        Map<String, Object> result = buildResult("ok", candidateUserIds.size(), insertedCount,
                insertedCount > 0 ? "已完成缺失用户回补" : "本轮无新增回补");
        result.put("taskId", latestTask.getId());
        result.put("snapshotDate", latestTask.getSnapshotDate());
        result.put("segmentCode", fallbackSegment.getSegmentCode());
        result.put("segmentName", fallbackSegment.getSegmentName());
        result.put("lookbackDays", safeLookbackDays);
        result.put("maxUsersPerRun", safeMaxUsersPerRun);
        return result;
    }

    public Map<String, Object> ensureUserAssignedToLatestTask(Long userId) {
        if (userId == null || userId <= 0) {
            return buildResult("skipped", 0, 0, "用户ID不合法，无法执行分群回补");
        }

        User targetUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getRole, Constants.Role.USER)
                .eq(User::getStatus, 1)
                .select(User::getId)
                .last("LIMIT 1"));
        if (targetUser == null) {
            return buildResult("skipped", 0, 0, "目标用户不存在或状态不可用，跳过回补");
        }

        AnalyticsKmeansTask latestTask = findLatestSuccessfulTask();
        boolean bootstrapCreated = false;
        if (latestTask == null || latestTask.getId() == null) {
            latestTask = createBootstrapSuccessTask();
            bootstrapCreated = latestTask != null && latestTask.getId() != null;
        }
        if (latestTask == null || latestTask.getId() == null) {
            return buildResult("failed", 1, 0, "无法创建可用分群任务，回补失败");
        }

        AnalyticsKmeansUserResult existing = userResultMapper.selectOne(new LambdaQueryWrapper<AnalyticsKmeansUserResult>()
                .eq(AnalyticsKmeansUserResult::getTaskId, latestTask.getId())
                .eq(AnalyticsKmeansUserResult::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null && existing.getId() != null) {
            Map<String, Object> result = buildResult("ok", 1, 0, "用户已在最新分群结果中");
            result.put("taskId", latestTask.getId());
            result.put("snapshotDate", latestTask.getSnapshotDate());
            result.put("segmentCode", existing.getSegmentCode());
            result.put("segmentName", existing.getSegmentName());
            result.put("bootstrapCreated", bootstrapCreated);
            return result;
        }

        AnalyticsKmeansSegment fallbackSegment = resolveFallbackSegment(latestTask);
        AnalyticsKmeansUserResult row = new AnalyticsKmeansUserResult();
        row.setTaskId(latestTask.getId());
        row.setSnapshotDate(latestTask.getSnapshotDate());
        row.setUserId(userId);
        row.setSegmentCode(fallbackSegment.getSegmentCode());
        row.setSegmentName(fallbackSegment.getSegmentName());
        row.setClusterIndex(null);
        row.setDistanceToCenter(null);
        row.setConfidenceScore(new BigDecimal("0.10"));
        row.setIsColdStart(1);
        row.setSortOrder(resolveNextSortOrder(latestTask.getId()));
        row.setPersonaSummary("自动回补: 待分群用户即时纳入冷启动分群");
        userResultMapper.insert(row);
        touchTaskAndSegmentMetrics(latestTask, fallbackSegment, 1);

        Map<String, Object> result = buildResult("ok", 1, 1, "已完成 COLD_START 回补并立即生效");
        result.put("taskId", latestTask.getId());
        result.put("snapshotDate", latestTask.getSnapshotDate());
        result.put("segmentCode", fallbackSegment.getSegmentCode());
        result.put("segmentName", fallbackSegment.getSegmentName());
        result.put("bootstrapCreated", bootstrapCreated);
        return result;
    }

    private AnalyticsKmeansTask findLatestSuccessfulTask() {
        return taskMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsKmeansTask>()
                        .eq(AnalyticsKmeansTask::getStatus, TASK_STATUS_SUCCESS)
                        .orderByDesc(AnalyticsKmeansTask::getSnapshotDate)
                        .orderByDesc(AnalyticsKmeansTask::getId)
                        .last("LIMIT 1"));
    }

    private AnalyticsKmeansTask createBootstrapSuccessTask() {
        LocalDateTime now = LocalDateTime.now();
        AnalyticsKmeansTask task = new AnalyticsKmeansTask();
        task.setBatchNo("bootstrap-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + Math.abs((int) (System.nanoTime() % 10000)));
        task.setSnapshotDate(LocalDate.now());
        task.setStatus(TASK_STATUS_SUCCESS);
        task.setAlgorithmName("kmeans");
        task.setModelVersion("bootstrap-cold-start-v1");
        task.setFeatureVersion("bootstrap-cold-start-v1");
        task.setClusterCount(1);
        task.setSampleUserCount(0L);
        task.setClusteredUserCount(0L);
        task.setColdStartUserCount(0L);
        Map<String, Object> resultSummary = new LinkedHashMap<>();
        resultSummary.put("source", "admin-auto-backfill");
        resultSummary.put("note", "自动创建 bootstrap 分群任务以承接 COLD_START 回补");
        task.setResultSummary(resultSummary);
        Map<String, Object> llmOverview = new LinkedHashMap<>();
        llmOverview.put("summary", "系统自动创建 bootstrap 分群任务，保证待分群用户可即时进入冷启动分群。");
        task.setLlmOverview(llmOverview);
        task.setStartTime(now);
        task.setEndTime(now);
        try {
            taskMapper.insert(task);
            resolveFallbackSegment(task);
            log.info("[KMeansBackfill] Created bootstrap task id={} batchNo={}", task.getId(), task.getBatchNo());
            return task;
        } catch (Exception exception) {
            log.warn("[KMeansBackfill] Create bootstrap task failed: {}", exception.getMessage());
            return findLatestSuccessfulTask();
        }
    }

    private Set<Long> loadCandidateUserIds(LocalDateTime lookbackStart, int safeMaxUsersPerRun) {
        Set<Long> candidates = new LinkedHashSet<>();

        List<Long> behaviorUserIds = userBehaviorMapper.selectActiveUserIdsSince(lookbackStart, safeMaxUsersPerRun * 2);
        if (behaviorUserIds != null) {
            for (Long userId : behaviorUserIds) {
                if (userId != null && userId > 0) {
                    candidates.add(userId);
                }
            }
        }

        List<Object> paidOrderUserRaw = orderMapper.selectObjs(
                new QueryWrapper<Order>()
                        .select("DISTINCT user_id")
                        .isNotNull("user_id")
                        .ge("create_time", lookbackStart)
                        .in("status", Arrays.asList(1, 2, 3))
                        .last("LIMIT " + (safeMaxUsersPerRun * 2)));
        if (paidOrderUserRaw != null) {
            for (Object raw : paidOrderUserRaw) {
                Long parsed = parseLong(raw);
                if (parsed != null && parsed > 0) {
                    candidates.add(parsed);
                }
            }
        }

        if (candidates.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> trimmedCandidates = new ArrayList<>(candidates);
        if (trimmedCandidates.size() > safeMaxUsersPerRun * 2) {
            trimmedCandidates = trimmedCandidates.subList(0, safeMaxUsersPerRun * 2);
        }

        List<User> validUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, trimmedCandidates)
                        .eq(User::getRole, Constants.Role.USER)
                        .eq(User::getStatus, 1)
                        .select(User::getId)
                        .orderByDesc(User::getId)
                        .last("LIMIT " + safeMaxUsersPerRun));

        return validUsers.stream()
                .map(User::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AnalyticsKmeansSegment resolveFallbackSegment(AnalyticsKmeansTask latestTask) {
        AnalyticsKmeansSegment segment = segmentMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsKmeansSegment>()
                        .eq(AnalyticsKmeansSegment::getTaskId, latestTask.getId())
                        .eq(AnalyticsKmeansSegment::getSegmentCode, DEFAULT_SEGMENT_CODE)
                        .last("LIMIT 1"));
        if (segment != null && StringUtils.hasText(segment.getSegmentCode())) {
            return segment;
        }

        AnalyticsKmeansSegment fallback = new AnalyticsKmeansSegment();
        fallback.setTaskId(latestTask.getId());
        fallback.setSnapshotDate(latestTask.getSnapshotDate());
        fallback.setSegmentCode(DEFAULT_SEGMENT_CODE);
        fallback.setSegmentName(DEFAULT_SEGMENT_NAME);
        fallback.setSegmentDescription("用于承接自动回补用户，保障推荐分群覆盖率。");
        fallback.setLlmSummary("自动回补分群");
        fallback.setOperationSuggestion("建议尽快补齐行为数据后重新跑 KMeans 分群。");
        fallback.setUserCount(0L);
        fallback.setPercentage(BigDecimal.ZERO);
        segmentMapper.insert(fallback);
        return fallback;
    }

    private int resolveNextSortOrder(Long taskId) {
        AnalyticsKmeansUserResult latestRow = userResultMapper.selectOne(
                new LambdaQueryWrapper<AnalyticsKmeansUserResult>()
                        .eq(AnalyticsKmeansUserResult::getTaskId, taskId)
                        .orderByDesc(AnalyticsKmeansUserResult::getSortOrder)
                        .orderByDesc(AnalyticsKmeansUserResult::getId)
                        .last("LIMIT 1"));
        Integer latestSortOrder = latestRow == null ? null : latestRow.getSortOrder();
        return latestSortOrder == null ? 1 : Math.max(1, latestSortOrder + 1);
    }

    private void touchTaskAndSegmentMetrics(AnalyticsKmeansTask latestTask,
                                            AnalyticsKmeansSegment fallbackSegment,
                                            int insertedCount) {
        if (insertedCount <= 0) {
            return;
        }

        Long baseClusteredCount = latestTask.getClusteredUserCount() == null ? 0L : latestTask.getClusteredUserCount();
        Long baseColdStartCount = latestTask.getColdStartUserCount() == null ? 0L : latestTask.getColdStartUserCount();
        latestTask.setClusteredUserCount(baseClusteredCount + insertedCount);
        latestTask.setColdStartUserCount(baseColdStartCount + insertedCount);
        taskMapper.updateById(latestTask);

        if (fallbackSegment != null && fallbackSegment.getId() != null) {
            Long baseUserCount = fallbackSegment.getUserCount() == null ? 0L : fallbackSegment.getUserCount();
            fallbackSegment.setUserCount(baseUserCount + insertedCount);
            segmentMapper.updateById(fallbackSegment);
        }
        recalcSegmentPercentage(latestTask.getId(), latestTask.getClusteredUserCount());
    }

    private void recalcSegmentPercentage(Long taskId, Long clusteredUserCount) {
        if (taskId == null) {
            return;
        }
        long denominator = clusteredUserCount == null ? 0L : Math.max(clusteredUserCount, 0L);
        List<AnalyticsKmeansSegment> segments = segmentMapper.selectList(
                new LambdaQueryWrapper<AnalyticsKmeansSegment>()
                        .eq(AnalyticsKmeansSegment::getTaskId, taskId));
        if (segments == null || segments.isEmpty()) {
            return;
        }
        for (AnalyticsKmeansSegment segment : segments) {
            if (segment == null || segment.getId() == null) {
                continue;
            }
            long count = segment.getUserCount() == null ? 0L : Math.max(segment.getUserCount(), 0L);
            BigDecimal percentage = denominator <= 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(count)
                    .multiply(new BigDecimal("100"))
                    .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
            segment.setPercentage(percentage);
            segmentMapper.updateById(segment);
        }
    }

    private Map<String, Object> buildResult(String status, int candidateCount, int insertedCount, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("candidateUserCount", candidateCount);
        result.put("insertedCount", insertedCount);
        result.put("message", message);
        return result;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }
}
