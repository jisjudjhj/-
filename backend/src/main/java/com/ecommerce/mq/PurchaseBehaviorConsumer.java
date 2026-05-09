package com.ecommerce.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.AnalyticsRecommendationExposure;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.AnalyticsRecommendationExposureMapper;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.recommendation.ABTestFramework;
import com.ecommerce.recommendation.CollaborativeFiltering;
import com.ecommerce.service.ModuleSwitchService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "mq", name = "enabled", havingValue = "true")
public class PurchaseBehaviorConsumer {

    private static final String CONSUMER_NAME = "purchase-behavior-consumer";

    private final MqConsumeLogService consumeLogService;
    private final ModuleSwitchService moduleSwitchService;
    private final OrderItemMapper orderItemMapper;
    private final UserBehaviorMapper userBehaviorMapper;
    private final AnalyticsRecommendationExposureMapper analyticsRecommendationExposureMapper;
    private final ABTestFramework abTestFramework;
    private final CollaborativeFiltering collaborativeFiltering;

    public PurchaseBehaviorConsumer(MqConsumeLogService consumeLogService,
                                    ModuleSwitchService moduleSwitchService,
                                    OrderItemMapper orderItemMapper,
                                    UserBehaviorMapper userBehaviorMapper,
                                    AnalyticsRecommendationExposureMapper analyticsRecommendationExposureMapper,
                                    ABTestFramework abTestFramework,
                                    CollaborativeFiltering collaborativeFiltering) {
        this.consumeLogService = consumeLogService;
        this.moduleSwitchService = moduleSwitchService;
        this.orderItemMapper = orderItemMapper;
        this.userBehaviorMapper = userBehaviorMapper;
        this.analyticsRecommendationExposureMapper = analyticsRecommendationExposureMapper;
        this.abTestFramework = abTestFramework;
        this.collaborativeFiltering = collaborativeFiltering;
    }

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = RabbitMqNames.BEHAVIOR_QUEUE, containerFactory = "mqListenerContainerFactory")
    public void consume(String rawMessage) {
        DomainEvent event = JSON.parseObject(rawMessage, DomainEvent.class);
        if (event == null || !consumeLogService.tryAcquire(event.getEventId(), CONSUMER_NAME)) {
            return;
        }
        if (!moduleSwitchService.isEnabled("recommendation")) {
            return;
        }

        JSONObject payload = event.getPayload();
        Long orderId = payload == null ? null : payload.getLong("orderId");
        Long userId = payload == null ? null : payload.getLong("userId");
        if (orderId == null || userId == null) {
            return;
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );

        LocalDateTime purchaseTime = LocalDateTime.now();
        for (OrderItem item : items) {
            UserBehavior behavior = new UserBehavior();
            behavior.setUserId(userId);
            behavior.setProductId(item.getProductId());
            behavior.setBehaviorType(Constants.BehaviorType.PURCHASE);
            userBehaviorMapper.insert(behavior);
            markRecommendationPurchaseAttribution(orderId, userId, item.getProductId(), purchaseTime);
        }
        collaborativeFiltering.invalidateUserVectorCache(userId);
    }

    private void markRecommendationPurchaseAttribution(Long orderId,
                                                       Long userId,
                                                       Long productId,
                                                       LocalDateTime purchaseTime) {
        List<AnalyticsRecommendationExposure> exposures = analyticsRecommendationExposureMapper.selectList(
                new LambdaQueryWrapper<AnalyticsRecommendationExposure>()
                        .eq(AnalyticsRecommendationExposure::getUserId, userId)
                        .eq(AnalyticsRecommendationExposure::getProductId, productId)
                        .isNull(AnalyticsRecommendationExposure::getPurchaseTime)
                        .ge(AnalyticsRecommendationExposure::getExposureTime, purchaseTime.minusDays(30))
                        .orderByDesc(AnalyticsRecommendationExposure::getExposureTime)
                        .last("LIMIT 10"));
        if (exposures == null || exposures.isEmpty()) {
            return;
        }

        AnalyticsRecommendationExposure selected = null;
        for (AnalyticsRecommendationExposure exposure : exposures) {
            if (exposure.getCartTime() != null) {
                selected = exposure;
                break;
            }
            if (selected == null && exposure.getClickTime() != null) {
                selected = exposure;
            }
            if (selected == null) {
                selected = exposure;
            }
        }
        if (selected == null) {
            return;
        }

        selected.setPurchaseTime(purchaseTime);
        selected.setOrderId(orderId);
        analyticsRecommendationExposureMapper.updateById(selected);

        if (StringUtils.hasText(selected.getExperimentGroup())
                && !"disabled".equalsIgnoreCase(selected.getExperimentGroup())) {
            abTestFramework.recordPurchase(userId, selected.getExperimentGroup(), productId);
        }
    }
}
