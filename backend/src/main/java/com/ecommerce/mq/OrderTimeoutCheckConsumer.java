package com.ecommerce.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ecommerce.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "mq", name = "enabled", havingValue = "true")
public class OrderTimeoutCheckConsumer {

    private static final String CONSUMER_NAME = "order-timeout-check-consumer";

    private final MqConsumeLogService consumeLogService;
    private final OrderService orderService;

    public OrderTimeoutCheckConsumer(MqConsumeLogService consumeLogService,
                                     OrderService orderService) {
        this.consumeLogService = consumeLogService;
        this.orderService = orderService;
    }

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = RabbitMqNames.ORDER_TIMEOUT_CHECK_QUEUE, containerFactory = "mqListenerContainerFactory")
    public void consume(String rawMessage) {
        DomainEvent event = JSON.parseObject(rawMessage, DomainEvent.class);
        if (event == null || !consumeLogService.tryAcquire(event.getEventId(), CONSUMER_NAME)) {
            return;
        }

        JSONObject payload = event.getPayload();
        Long orderId = payload == null ? null : payload.getLong("orderId");
        if (orderId != null) {
            orderService.cancelTimeoutOrder(orderId);
        }
    }
}
