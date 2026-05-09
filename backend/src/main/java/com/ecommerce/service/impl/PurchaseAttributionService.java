package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.dto.RecommendationEventDTO;
import com.ecommerce.entity.AnalyticsRecommendationExposure;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.mapper.AnalyticsRecommendationExposureMapper;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.recommendation.ABTestFramework;
import com.ecommerce.recommendation.CollaborativeFiltering;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.RecommendationAsyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购买行为记录与推荐归因服务
 * 从 OrderServiceImpl 中提取，减少订单核心服务的职责
 */
@Service
public class PurchaseAttributionService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseAttributionService.class);

    @Autowired
    private UserBehaviorMapper userBehaviorMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private AnalyticsRecommendationExposureMapper analyticsRecommendationExposureMapper;

    @Autowired
    private ABTestFramework abTestFramework;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private CollaborativeFiltering collaborativeFiltering;

    @Autowired
    private RecommendationAsyncService recommendationAsyncService;

    public void recordPurchaseBehavior(Long orderId, Long userId) {
        if (!moduleSwitchService.isEnabled("recommendation")) {
            return;
        }

        LocalDateTime purchaseTime = LocalDateTime.now();
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        for (OrderItem item : items) {
            UserBehavior behavior = new UserBehavior();
            behavior.setUserId(userId);
            behavior.setProductId(item.getProductId());
            behavior.setBehaviorType(Constants.BehaviorType.PURCHASE);
            behavior.setOrderId(orderId);
            userBehaviorMapper.insert(behavior);

            RecommendationEventDTO eventDTO = new RecommendationEventDTO();
            eventDTO.setEventType(Constants.RecommendationEventType.ORDER);
            eventDTO.setProductId(item.getProductId());
            eventDTO.setOrderId(orderId);
            eventDTO.setEventTime(purchaseTime);
            eventDTO.setTraceId(orderId == null ? null : "order-" + orderId);
            eventDTO.setScene("order_pay");
            recommendationAsyncService.recordRecommendationEventAsync(userId, eventDTO);

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
