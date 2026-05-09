package com.ecommerce.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.Message;
import com.ecommerce.entity.Order;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.mq.MqEventPublisher;
import com.ecommerce.mq.RabbitMqNames;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.BiConsumer;

/**
 * 订单消息通知帮助类
 * 统一处理订单相关的消息发送逻辑，消除重复代码
 */
@Component
public class OrderMessageHelper {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageHelper.class);

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private MqEventPublisher mqEventPublisher;

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @Value("${order.timeout-minutes:30}")
    private int timeoutMinutes;

    /**
     * 订单创建通知
     */
    public void notifyOrderCreated(Order order, BigDecimal finalAmount, BigDecimal discountAmount) {
        String title = "下单成功";
        String content = buildOrderCreatedContent(order, finalAmount, discountAmount);
        
        sendOrderNotification(order, title, content, RabbitMqNames.ROUTING_ORDER_CREATED, () -> {
            // 额外发送超时调度消息
            publishTimeoutSchedule(order);
        });
    }

    /**
     * 订单支付成功通知
     */
    public void notifyOrderPaid(Order order, BigDecimal totalAmount) {
        String title = "支付成功";
        String content = "您的订单 " + order.getOrderNo() + " 已支付成功，金额 ¥" + totalAmount.toPlainString();
        sendOrderNotification(order, title, content, RabbitMqNames.ROUTING_ORDER_PAID, null);
    }

    /**
     * 订单取消通知
     */
    public void notifyOrderCancelled(Order order, boolean wasPaid) {
        String title = "订单已取消";
        String content = "您的订单 " + order.getOrderNo() + " 已取消" + (wasPaid ? "，退款已到账" : "");
        sendOrderNotification(order, title, content, RabbitMqNames.ROUTING_ORDER_CANCELLED, null);
    }

    /**
     * 订单超时自动取消通知
     */
    public void notifyOrderTimeout(Order order) {
        String title = "订单已自动取消";
        String content = "您的订单 " + order.getOrderNo() + " 因超时未支付，已自动取消";
        sendOrderNotification(order, title, content, RabbitMqNames.ROUTING_ORDER_TIMEOUT_CANCELLED, null);
    }

    /**
     * 订单状态变更通知
     */
    public void notifyOrderStatusChanged(Order order, String statusName) {
        String title = "订单状态更新";
        String content = "您的订单 " + order.getOrderNo() + " 状态已更新为 " + statusName;
        sendOrderNotification(order, title, content, RabbitMqNames.ROUTING_ORDER_STATUS_CHANGED, null);
    }

    /**
     * 统一发送订单通知
     */
    private void sendOrderNotification(Order order, String title, String content, 
                                       String routingKey, Runnable extraAction) {
        if (mqEventPublisher.isEnabled()) {
            publishOrderMessageEvent(routingKey, order, title, content);
        } else {
            sendSyncMessage(order.getUserId(), title, content, order.getId());
        }
        
        if (extraAction != null) {
            extraAction.run();
        }
    }

    /**
     * 构建订单创建消息内容
     */
    private String buildOrderCreatedContent(Order order, BigDecimal finalAmount, BigDecimal discountAmount) {
        StringBuilder sb = new StringBuilder();
        sb.append("您的订单 ").append(order.getOrderNo()).append(" 已创建，金额 ¥")
          .append(finalAmount.toPlainString());
        
        if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("（优惠 ¥").append(discountAmount.toPlainString()).append("）");
        }
        
        sb.append("，请在").append(timeoutMinutes).append("分钟内完成支付");
        return sb.toString();
    }

    /**
     * 发布订单消息事件到MQ
     */
    private void publishOrderMessageEvent(String routingKey, Order order, String title, String content) {
        JSONObject payload = buildNotificationPayload(
                order.getUserId(),
                title,
                content,
                Constants.MessageType.ORDER,
                order.getId()
        );
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("totalAmount", order.getTotalAmount());
        mqEventPublisher.publishEvent(routingKey, String.valueOf(order.getId()), payload);
    }

    /**
     * 发布订单超时调度消息
     */
    private void publishTimeoutSchedule(Order order) {
        JSONObject timeoutPayload = new JSONObject();
        timeoutPayload.put("orderId", order.getId());
        timeoutPayload.put("userId", order.getUserId());
        timeoutPayload.put("orderNo", order.getOrderNo());
        mqEventPublisher.publishDelay(
                RabbitMqNames.ROUTING_ORDER_TIMEOUT_SCHEDULE,
                String.valueOf(order.getId()),
                timeoutPayload
        );
    }

    /**
     * 同步发送消息（非MQ模式）
     */
    private void sendSyncMessage(Long userId, String title, String content, Long orderId) {
        if (!moduleSwitchService.isEnabled("message")) {
            return;
        }
        
        Message message = new Message();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setType(Constants.MessageType.ORDER);
        message.setRelatedId(orderId);
        message.setIsRead(0);
        messageMapper.insert(message);
        managementWorkbenchRealtimeService.notifyUserMessageChanged(userId, "user-message-created");
        managementWorkbenchRealtimeService.notifyMerchantMessageChanged(userId, "merchant-message-created");
    }

    /**
     * 构建通知消息体
     */
    private JSONObject buildNotificationPayload(Long userId, String title, String content,
                                                String messageType, Long relatedId) {
        JSONObject payload = new JSONObject();
        payload.put("userId", userId);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("messageType", messageType);
        payload.put("relatedId", relatedId);
        return payload;
    }
}
