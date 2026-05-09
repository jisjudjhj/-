package com.ecommerce.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.config.AnalyticsKmeansCampaignProperties;
import com.ecommerce.entity.AnalyticsKmeansSegment;
import com.ecommerce.entity.AnalyticsKmeansTask;
import com.ecommerce.entity.Coupon;
import com.ecommerce.entity.Message;
import com.ecommerce.entity.UserCoupon;
import com.ecommerce.mapper.AnalyticsKmeansSegmentMapper;
import com.ecommerce.mapper.AnalyticsKmeansTaskMapper;
import com.ecommerce.mapper.AnalyticsKmeansUserResultMapper;
import com.ecommerce.mapper.CouponMapper;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.mapper.UserCouponMapper;
import com.ecommerce.mq.MqEventPublisher;
import com.ecommerce.mq.RabbitMqNames;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.utils.AnalyticsKmeansSegmentProfileUtil;
import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class KmeansLowActivityCouponCampaignService {

    private static final Logger log = LoggerFactory.getLogger(KmeansLowActivityCouponCampaignService.class);

    private static final String TASK_STATUS_SUCCESS = "success";
    private static final String TRIGGER_MODE_MANUAL = "manual";
    private static final String STATUS_FAILED = "failed";
    private static final String CAMPAIGN_SUMMARY_KEY = "lowActivityCouponCampaigns";
    private static final String CAMPAIGN_LAST_SUMMARY_KEY = "lowActivityCouponCampaign";
    private static final String CACHE_KEY_LATEST_TASK = "analytics:kmeans:latest:task";
    private static final String CACHE_KEY_LATEST_SUMMARY = "analytics:kmeans:latest:summary";
    private static final String CACHE_KEY_LATEST_SEGMENTS = "analytics:kmeans:latest:segments";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int AUTO_SCAN_PAGE_SIZE_MIN = 20;

    private final AnalyticsKmeansCampaignProperties campaignProperties;
    private final AnalyticsKmeansTaskMapper taskMapper;
    private final AnalyticsKmeansSegmentMapper segmentMapper;
    private final AnalyticsKmeansUserResultMapper userResultMapper;
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final MessageMapper messageMapper;
    private final MqEventPublisher mqEventPublisher;
    private final ModuleSwitchService moduleSwitchService;
    private final ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;
    private final RedisUtil redisUtil;
    private final ConcurrentMap<String, ReentrantLock> campaignLocks = new ConcurrentHashMap<>();

    public KmeansLowActivityCouponCampaignService(AnalyticsKmeansCampaignProperties campaignProperties,
                                                  AnalyticsKmeansTaskMapper taskMapper,
                                                  AnalyticsKmeansSegmentMapper segmentMapper,
                                                  AnalyticsKmeansUserResultMapper userResultMapper,
                                                  CouponMapper couponMapper,
                                                  UserCouponMapper userCouponMapper,
                                                  MessageMapper messageMapper,
                                                  MqEventPublisher mqEventPublisher,
                                                  ModuleSwitchService moduleSwitchService,
                                                  ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService,
                                                  RedisUtil redisUtil) {
        this.campaignProperties = campaignProperties;
        this.taskMapper = taskMapper;
        this.segmentMapper = segmentMapper;
        this.userResultMapper = userResultMapper;
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.messageMapper = messageMapper;
        this.mqEventPublisher = mqEventPublisher;
        this.moduleSwitchService = moduleSwitchService;
        this.managementWorkbenchRealtimeService = managementWorkbenchRealtimeService;
        this.redisUtil = redisUtil;
    }

    public List<Long> listPendingAutoTaskIds() {
        if (!campaignProperties.isEnabled()
                || !campaignProperties.isAutoProcessEnabled()
                || campaignProperties.getDefaultCouponId() == null
                || campaignProperties.getDefaultCouponId() <= 0
                || !moduleSwitchService.isEnabled("coupon")) {
            return Collections.emptyList();
        }

        int scanLimit = Math.max(1, campaignProperties.getScanLimit());
        long pageNo = 1L;
        long pageSize = Math.max(AUTO_SCAN_PAGE_SIZE_MIN, scanLimit * 5L);
        String campaignKey = buildCampaignEntryKey(campaignProperties.getDefaultCouponId());
        List<Long> pendingTaskIds = new ArrayList<>();
        LocalDate snapshotStartDate = resolveSnapshotStartDate();

        while (pendingTaskIds.size() < scanLimit) {
            LambdaQueryWrapper<AnalyticsKmeansTask> queryWrapper = new LambdaQueryWrapper<AnalyticsKmeansTask>()
                    .eq(AnalyticsKmeansTask::getStatus, TASK_STATUS_SUCCESS);
            if (snapshotStartDate != null) {
                queryWrapper.ge(AnalyticsKmeansTask::getSnapshotDate, snapshotStartDate);
            }
            queryWrapper.orderByDesc(AnalyticsKmeansTask::getSnapshotDate)
                    .orderByDesc(AnalyticsKmeansTask::getId);

            IPage<AnalyticsKmeansTask> page = taskMapper.selectPage(
                    new Page<>(pageNo, pageSize),
                    queryWrapper
            );
            List<AnalyticsKmeansTask> records = page == null ? Collections.emptyList() : page.getRecords();
            if (records.isEmpty()) {
                break;
            }

            for (AnalyticsKmeansTask task : records) {
                Map<String, Object> campaigns = readCampaignSummaries(task.getResultSummary());
                if (!campaigns.containsKey(campaignKey)) {
                    pendingTaskIds.add(task.getId());
                    if (pendingTaskIds.size() >= scanLimit) {
                        break;
                    }
                }
            }

            if (page == null || pageNo >= page.getPages()) {
                break;
            }
            pageNo++;
        }
        return pendingTaskIds;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeCampaign(Long taskId,
                                               Long couponId,
                                               boolean sendNotification,
                                               String triggerMode) {
        String lockKey = buildExecutionLockKey(taskId);
        ReentrantLock lock = campaignLocks.computeIfAbsent(lockKey, key -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new BusinessException(409, "当前任务与优惠券模板的发券流程正在执行，请稍后重试");
        }
        try {
            return doExecuteCampaign(taskId, couponId, sendNotification, triggerMode);
        } finally {
            lock.unlock();
            campaignLocks.remove(lockKey, lock);
        }
    }

    private Map<String, Object> doExecuteCampaign(Long taskId,
                                                  Long couponId,
                                                  boolean sendNotification,
                                                  String triggerMode) {
        if (!campaignProperties.isEnabled()) {
            throw new BusinessException(503, "低活跃用户自动发券功能未启用");
        }
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(400, "任务ID不能为空");
        }
        if (couponId == null || couponId <= 0) {
            throw new BusinessException(400, "优惠券模板ID不能为空");
        }

        AnalyticsKmeansTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "分群任务不存在");
        }
        if (!TASK_STATUS_SUCCESS.equals(task.getStatus())) {
            throw new BusinessException(400, "只有成功完成的分群任务才能执行发券");
        }

        moduleSwitchService.requireEnabled("coupon");

        List<AnalyticsKmeansSegment> lowActivitySegments = loadLowActivitySegments(taskId);
        List<String> segmentCodes = lowActivitySegments.stream()
                .map(AnalyticsKmeansSegment::getSegmentCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        List<String> segmentNames = lowActivitySegments.stream()
                .map(AnalyticsKmeansSegment::getSegmentName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        if (segmentCodes.isEmpty()) {
            Map<String, Object> summary = buildBaseSummary(task, couponId, triggerMode);
            summary.put("status", "no_low_activity_segment");
            summary.put("targetSegmentCodes", Collections.emptyList());
            summary.put("targetSegmentNames", Collections.emptyList());
            summary.put("targetUserCount", 0);
            summary.put("candidateUserCount", 0);
            summary.put("grantedUserCount", 0);
            persistCampaignSummary(task, couponId, summary);
            return summary;
        }

        Coupon coupon = loadAndValidateCoupon(couponId);
        List<Long> targetUserIds = userResultMapper.selectUserIdsByTaskAndSegmentCodes(taskId, segmentCodes);

        Map<String, Object> summary = buildBaseSummary(task, couponId, triggerMode);
        summary.put("couponName", coupon.getName());
        summary.put("targetSegmentCodes", segmentCodes);
        summary.put("targetSegmentNames", segmentNames);
        summary.put("targetUserCount", targetUserIds.size());

        if (targetUserIds.isEmpty()) {
            summary.put("status", "no_target_user");
            summary.put("candidateUserCount", 0);
            summary.put("grantedUserCount", 0);
            persistCampaignSummary(task, couponId, summary);
            return summary;
        }

        Set<Long> existingCouponUserIds = new LinkedHashSet<>(userCouponMapper.selectOwnedUserIds(couponId, targetUserIds));
        List<Long> candidateUserIds = targetUserIds.stream()
                .filter(userId -> !existingCouponUserIds.contains(userId))
                .collect(Collectors.toList());

        summary.put("existingCouponUserCount", existingCouponUserIds.size());
        summary.put("candidateUserCount", candidateUserIds.size());

        if (candidateUserIds.isEmpty()) {
            summary.put("status", "already_granted");
            summary.put("grantedUserCount", 0);
            persistCampaignSummary(task, couponId, summary);
            return summary;
        }

        int remainingCount = getRemainingCouponCount(coupon);
        if (remainingCount <= 0) {
            summary.put("status", "no_available_coupon_stock");
            summary.put("grantedUserCount", 0);
            summary.put("outOfStockUserCount", candidateUserIds.size());
            persistCampaignSummary(task, couponId, summary);
            return summary;
        }

        int requestedGrantCount = Math.min(remainingCount, candidateUserIds.size());
        int reservedGrantCount = reserveCouponStock(couponId, requestedGrantCount);
        if (reservedGrantCount <= 0) {
            summary.put("status", "no_available_coupon_stock");
            summary.put("grantedUserCount", 0);
            summary.put("outOfStockUserCount", candidateUserIds.size());
            persistCampaignSummary(task, couponId, summary);
            return summary;
        }

        List<Long> plannedUserIds = new ArrayList<>(candidateUserIds.subList(0, reservedGrantCount));
        int insertedCount = userCouponMapper.batchInsertIgnore(buildUserCoupons(plannedUserIds, couponId));
        int duplicateSkipCount = reservedGrantCount - insertedCount;
        if (duplicateSkipCount > 0) {
            couponMapper.decrementUsedCountByAmount(couponId, duplicateSkipCount);
        }

        List<Long> grantedUserIds = userCouponMapper.selectOwnedUserIds(couponId, plannedUserIds);
        int deliveredNotificationUserCount = 0;
        int queuedNotificationUserCount = 0;
        String notificationChannel = "none";
        if (sendNotification && !grantedUserIds.isEmpty()) {
            NotificationResult notificationResult = notifyUsers(grantedUserIds, coupon);
            deliveredNotificationUserCount = notificationResult.deliveredUserCount;
            queuedNotificationUserCount = notificationResult.queuedUserCount;
            notificationChannel = notificationResult.channel;
        }

        Coupon latestCoupon = couponMapper.selectById(couponId);
        int remainingAfterGrant = latestCoupon == null ? 0 : getRemainingCouponCount(latestCoupon);

        summary.put("plannedGrantUserCount", reservedGrantCount);
        summary.put("grantedUserCount", grantedUserIds.size());
        summary.put("notifiedUserCount", deliveredNotificationUserCount);
        summary.put("deliveredNotificationUserCount", deliveredNotificationUserCount);
        summary.put("queuedNotificationUserCount", queuedNotificationUserCount);
        summary.put("notificationAcceptedUserCount", deliveredNotificationUserCount + queuedNotificationUserCount);
        summary.put("notificationChannel", notificationChannel);
        summary.put("outOfStockUserCount", Math.max(0, candidateUserIds.size() - reservedGrantCount));
        summary.put("skippedDuplicateUserCount", duplicateSkipCount);
        summary.put("remainingCouponCount", Math.max(0, remainingAfterGrant));
        summary.put("sendNotificationRequested", sendNotification);
        summary.put("status", grantedUserIds.isEmpty() ? "already_granted" : "success");
        persistCampaignSummary(task, couponId, summary);

        log.info("K-means low-activity coupon campaign completed: taskId={}, couponId={}, granted={}, deliveredNotifications={}, queuedNotifications={}, triggerMode={}",
                taskId, couponId, grantedUserIds.size(), deliveredNotificationUserCount, queuedNotificationUserCount, triggerMode);
        return summary;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recordCampaignFailure(Long taskId,
                                                     Long couponId,
                                                     String triggerMode,
                                                     String failureCode,
                                                     String errorMessage) {
        AnalyticsKmeansTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> summary = buildBaseSummary(task, couponId, triggerMode);
        summary.put("status", STATUS_FAILED);
        summary.put("failureCode", failureCode);
        summary.put("errorMessage", errorMessage);
        persistCampaignSummary(task, couponId, summary);
        return summary;
    }

    private List<AnalyticsKmeansSegment> loadLowActivitySegments(Long taskId) {
        List<AnalyticsKmeansSegment> segments = segmentMapper.selectList(
                new LambdaQueryWrapper<AnalyticsKmeansSegment>()
                        .eq(AnalyticsKmeansSegment::getTaskId, taskId)
                        .orderByAsc(AnalyticsKmeansSegment::getId)
        );
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }
        return segments.stream()
                .filter(AnalyticsKmeansSegmentProfileUtil::isLowActivitySegment)
                .collect(Collectors.toList());
    }

    private Coupon loadAndValidateCoupon(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException(404, "优惠券模板不存在");
        }
        if (coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new BusinessException(400, "优惠券模板未启用");
        }

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() == null || coupon.getEndTime() == null
                || now.isBefore(coupon.getStartTime())
                || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException(400, "优惠券模板不在有效期内");
        }
        return coupon;
    }

    private int reserveCouponStock(Long couponId, int amount) {
        int reserveAmount = amount;
        for (int attempt = 0; attempt < 3 && reserveAmount > 0; attempt++) {
            if (couponMapper.incrementUsedCountByAmount(couponId, reserveAmount) > 0) {
                return reserveAmount;
            }
            Coupon latestCoupon = couponMapper.selectById(couponId);
            if (latestCoupon == null
                    || latestCoupon.getStatus() == null
                    || latestCoupon.getStatus() != 1
                    || latestCoupon.getStartTime() == null
                    || latestCoupon.getEndTime() == null) {
                break;
            }
            reserveAmount = Math.min(getRemainingCouponCount(latestCoupon), reserveAmount);
            if (reserveAmount == 0) {
                break;
            }
        }
        if (reserveAmount <= 0) {
            return 0;
        }
        throw new BusinessException(409, "优惠券库存不足，请稍后重试");
    }

    private List<UserCoupon> buildUserCoupons(Collection<Long> userIds, Long couponId) {
        List<UserCoupon> userCoupons = new ArrayList<>();
        for (Long userId : userIds) {
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setUserId(userId);
            userCoupon.setCouponId(couponId);
            userCoupon.setStatus(0);
            userCoupons.add(userCoupon);
        }
        return userCoupons;
    }

    private NotificationResult notifyUsers(List<Long> userIds, Coupon coupon) {
        if (!moduleSwitchService.isEnabled("message")) {
            return new NotificationResult(0, 0, "skipped_message_module_disabled");
        }

        String title = StringUtils.hasText(campaignProperties.getNotificationTitle())
                ? campaignProperties.getNotificationTitle() : "专属唤醒优惠券已到账";
        String content = buildNotificationContent(coupon);
        String messageType = StringUtils.hasText(campaignProperties.getNotificationMessageType())
                ? campaignProperties.getNotificationMessageType() : "promotion";

        if (mqEventPublisher.isEnabled()) {
            for (Long userId : userIds) {
                JSONObject payload = new JSONObject();
                payload.put("userId", userId);
                payload.put("title", title);
                payload.put("content", content);
                payload.put("messageType", messageType);
                payload.put("relatedId", coupon.getId());
                mqEventPublisher.publishEvent(
                        RabbitMqNames.ROUTING_COUPON_ASSIGNED,
                        "kmeans-coupon-" + coupon.getId() + "-" + userId,
                        payload
                );
            }
            return new NotificationResult(0, userIds.size(), "mq");
        }

        for (Long userId : userIds) {
            Message message = new Message();
            message.setUserId(userId);
            message.setTitle(title);
            message.setContent(content);
            message.setType(messageType);
            message.setRelatedId(coupon.getId());
            message.setIsRead(0);
            messageMapper.insert(message);
            managementWorkbenchRealtimeService.notifyUserMessageChanged(userId, "user-message-created");
            managementWorkbenchRealtimeService.notifyMerchantMessageChanged(userId, "merchant-message-created");
        }
        return new NotificationResult(userIds.size(), 0, "direct");
    }

    private String buildNotificationContent(Coupon coupon) {
        String couponSummary;
        if (coupon.getType() != null && coupon.getType() == 1) {
            couponSummary = "满减券，面额" + safeCouponValue(coupon.getValue()) + "元";
        } else if (coupon.getType() != null && coupon.getType() == 2) {
            couponSummary = "折扣券，折扣" + safeCouponValue(coupon.getValue()) + "折";
        } else {
            couponSummary = "优惠券，面额" + safeCouponValue(coupon.getValue()) + "元";
        }
        String endTimeText = coupon.getEndTime() == null
                ? "有效期内"
                : coupon.getEndTime().format(DATE_TIME_FORMATTER) + "前";
        return "您收到一张" + coupon.getName() + "，类型为" + couponSummary + "，请在" + endTimeText + "使用。";
    }

    private String safeCouponValue(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private int getRemainingCouponCount(Coupon coupon) {
        int totalCount = coupon.getTotalCount() == null ? 0 : coupon.getTotalCount();
        int usedCount = coupon.getUsedCount() == null ? 0 : coupon.getUsedCount();
        return Math.max(0, totalCount - usedCount);
    }

    private Map<String, Object> buildBaseSummary(AnalyticsKmeansTask task, Long couponId, String triggerMode) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", task.getId());
        summary.put("batchNo", task.getBatchNo());
        summary.put("snapshotDate", task.getSnapshotDate());
        summary.put("couponId", couponId);
        summary.put("triggerMode", StringUtils.hasText(triggerMode) ? triggerMode : TRIGGER_MODE_MANUAL);
        summary.put("processedAt", LocalDateTime.now());
        summary.put("notifiedUserCount", 0);
        summary.put("deliveredNotificationUserCount", 0);
        summary.put("queuedNotificationUserCount", 0);
        summary.put("notificationAcceptedUserCount", 0);
        summary.put("notificationChannel", "none");
        return summary;
    }

    private LocalDate resolveSnapshotStartDate() {
        int recentDays = campaignProperties.getRecentDays();
        if (recentDays <= 0) {
            return null;
        }
        return LocalDate.now().minusDays(Math.max(0L, recentDays - 1L));
    }

    private void persistCampaignSummary(AnalyticsKmeansTask task, Long couponId, Map<String, Object> summary) {
        AnalyticsKmeansTask latestTask = taskMapper.selectById(task.getId());
        Map<String, Object> currentResultSummary = latestTask != null ? latestTask.getResultSummary() : task.getResultSummary();
        Map<String, Object> resultSummary = currentResultSummary == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<>(currentResultSummary);
        Map<String, Object> campaigns = new LinkedHashMap<>(readCampaignSummaries(currentResultSummary));
        campaigns.put(buildCampaignEntryKey(couponId), new LinkedHashMap<>(summary));
        resultSummary.put(CAMPAIGN_SUMMARY_KEY, campaigns);
        resultSummary.put(CAMPAIGN_LAST_SUMMARY_KEY, new LinkedHashMap<>(summary));

        AnalyticsKmeansTask update = new AnalyticsKmeansTask();
        update.setId(task.getId());
        update.setResultSummary(resultSummary);
        taskMapper.updateById(update);

        task.setResultSummary(resultSummary);
        clearKmeansCaches();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readCampaignSummaries(Map<String, Object> resultSummary) {
        if (resultSummary == null) {
            return Collections.emptyMap();
        }
        Object campaigns = resultSummary.get(CAMPAIGN_SUMMARY_KEY);
        if (campaigns instanceof Map) {
            return (Map<String, Object>) campaigns;
        }
        return Collections.emptyMap();
    }

    private String buildCampaignEntryKey(Long couponId) {
        return "coupon_" + couponId;
    }

    private String buildExecutionLockKey(Long taskId) {
        return "task_" + taskId;
    }

    private void clearKmeansCaches() {
        redisUtil.delete(CACHE_KEY_LATEST_TASK);
        redisUtil.delete(CACHE_KEY_LATEST_SUMMARY);
        redisUtil.delete(CACHE_KEY_LATEST_SEGMENTS);
    }

    private static class NotificationResult {
        private final int deliveredUserCount;
        private final int queuedUserCount;
        private final String channel;

        private NotificationResult(int deliveredUserCount, int queuedUserCount, String channel) {
            this.deliveredUserCount = deliveredUserCount;
            this.queuedUserCount = queuedUserCount;
            this.channel = channel;
        }
    }
}
