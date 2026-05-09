package com.ecommerce.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.Message;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class AnalyticsDataQualityScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsDataQualityScheduler.class);

    private final OrderMapper orderMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;

    @Value("${analytics.data-quality.enabled:true}")
    private boolean enabled;

    @Value("${analytics.data-quality.lookback-days:180}")
    private int lookbackDays;

    @Value("${analytics.data-quality.notify-admin:true}")
    private boolean notifyAdmin;

    @Value("${analytics.data-quality.message-type:system}")
    private String messageType;

    @Value("${analytics.data-quality.notification-title:数据质量巡检告警}")
    private String notificationTitle;

    @Value("${analytics.data-quality.gap-neighbor-days:3}")
    private int gapNeighborDays;

    @Value("${analytics.data-quality.min-positive-days:5}")
    private int minPositiveDays;

    public AnalyticsDataQualityScheduler(OrderMapper orderMapper,
                                         UserBehaviorMapper userBehaviorMapper,
                                         UserMapper userMapper,
                                         MessageMapper messageMapper) {
        this.orderMapper = orderMapper;
        this.userBehaviorMapper = userBehaviorMapper;
        this.userMapper = userMapper;
        this.messageMapper = messageMapper;
    }

    @Scheduled(cron = "${analytics.data-quality.cron:0 15 3 * * ?}")
    public void validateRecent180Days() {
        if (!enabled) {
            return;
        }

        int safeLookbackDays = Math.max(7, Math.min(lookbackDays, 180));
        int safeNeighborDays = Math.max(1, Math.min(gapNeighborDays, 14));
        int safeMinPositiveDays = Math.max(1, Math.min(minPositiveDays, safeLookbackDays));
        LocalDate today = LocalDate.now();
        List<LocalDate> checkDays = new ArrayList<>();
        List<Long> orderCounts = new ArrayList<>();
        List<Long> behaviorCounts = new ArrayList<>();

        for (int i = 1; i <= safeLookbackDays; i++) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();

            Long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .ge(Order::getCreateTime, start)
                    .lt(Order::getCreateTime, end));
            Long behaviorCount = userBehaviorMapper.selectCount(new LambdaQueryWrapper<UserBehavior>()
                    .ge(UserBehavior::getCreateTime, start)
                    .lt(UserBehavior::getCreateTime, end));
            checkDays.add(day);
            orderCounts.add(orderCount == null ? -1L : orderCount);
            behaviorCounts.add(behaviorCount == null ? -1L : behaviorCount);
        }

        List<String> missingOrderDays = new ArrayList<>();
        List<String> missingBehaviorDays = new ArrayList<>();
        if (countPositiveDays(orderCounts) >= safeMinPositiveDays) {
            for (int i = 0; i < checkDays.size(); i++) {
                if (isGapDay(orderCounts, i, safeNeighborDays)) {
                    missingOrderDays.add(checkDays.get(i).toString());
                }
            }
        }
        if (countPositiveDays(behaviorCounts) >= safeMinPositiveDays) {
            for (int i = 0; i < checkDays.size(); i++) {
                if (isGapDay(behaviorCounts, i, safeNeighborDays)) {
                    missingBehaviorDays.add(checkDays.get(i).toString());
                }
            }
        }

        if (missingOrderDays.isEmpty() && missingBehaviorDays.isEmpty()) {
            log.info("Analytics data quality check passed for recent {} days.", safeLookbackDays);
            return;
        }

        String content = buildAlertContent(safeLookbackDays, missingOrderDays, missingBehaviorDays);
        log.warn("Analytics data quality gaps detected: {}", content);

        if (notifyAdmin) {
            notifyAdmins(content);
        }
    }

    private String buildAlertContent(int safeLookbackDays,
                                     List<String> missingOrderDays,
                                     List<String> missingBehaviorDays) {
        StringBuilder builder = new StringBuilder();
        builder.append("最近 ").append(safeLookbackDays).append(" 天数据巡检发现疑似断档。");
        if (!missingOrderDays.isEmpty()) {
            builder.append(" 订单疑似缺失 ")
                    .append(missingOrderDays.size())
                    .append(" 天：")
                    .append(joinPreview(missingOrderDays));
        }
        if (!missingBehaviorDays.isEmpty()) {
            builder.append(" 行为疑似缺失 ")
                    .append(missingBehaviorDays.size())
                    .append(" 天：")
                    .append(joinPreview(missingBehaviorDays));
        }
        builder.append(" 请检查种子数据、埋点写入和定时任务执行状态。");
        return builder.toString();
    }

    private long countPositiveDays(List<Long> counts) {
        return counts.stream().filter(value -> value != null && value > 0).count();
    }

    private boolean isGapDay(List<Long> counts, int currentIndex, int neighborDays) {
        if (counts == null || currentIndex < 0 || currentIndex >= counts.size()) {
            return false;
        }
        Long current = counts.get(currentIndex);
        if (current == null || current < 0) {
            return true;
        }
        if (current > 0) {
            return false;
        }
        int start = Math.max(0, currentIndex - neighborDays);
        int end = Math.min(counts.size() - 1, currentIndex + neighborDays);
        for (int idx = start; idx <= end; idx++) {
            if (idx == currentIndex) {
                continue;
            }
            Long value = counts.get(idx);
            if (value != null && value > 0) {
                return true;
            }
        }
        return false;
    }

    private String joinPreview(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return "-";
        }
        int previewSize = Math.min(values.size(), 10);
        String preview = String.join("、", values.subList(0, previewSize));
        if (values.size() > previewSize) {
            preview += " 等";
        }
        return preview;
    }

    private void notifyAdmins(String content) {
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, Constants.Role.ADMIN)
                .eq(User::getStatus, 1)
                .select(User::getId));
        if (CollectionUtils.isEmpty(admins)) {
            log.warn("Analytics data quality alert skipped because no active admin users were found.");
            return;
        }

        String finalType = StringUtils.hasText(messageType) ? messageType : Constants.MessageType.SYSTEM;
        for (User admin : admins) {
            if (admin == null || admin.getId() == null) {
                continue;
            }
            Message message = new Message();
            message.setUserId(admin.getId());
            message.setTitle(notificationTitle);
            message.setContent(content);
            message.setType(finalType);
            message.setRelatedId(null);
            message.setIsRead(0);
            messageMapper.insert(message);
        }
    }
}
