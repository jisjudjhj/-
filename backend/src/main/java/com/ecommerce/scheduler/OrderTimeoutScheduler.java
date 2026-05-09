package com.ecommerce.scheduler;

import com.ecommerce.entity.Order;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mq.MqEventPublisher;
import com.ecommerce.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    @Value("${order.timeout-minutes:30}")
    private int timeoutMinutes;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MqEventPublisher mqEventPublisher;

    @Scheduled(fixedRate = 60000)
    public void cancelTimeoutOrders() {
        if (mqEventPublisher.isEnabled()) {
            return;
        }

        List<Order> timeoutOrders = orderMapper.selectTimeoutPendingOrders(timeoutMinutes);
        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("[订单超时] 扫描到 {} 笔超时未支付订单，开始处理", timeoutOrders.size());

        for (Order order : timeoutOrders) {
            try {
                orderService.cancelTimeoutOrder(order.getId());
            } catch (Exception e) {
                log.error("[订单超时] 处理订单 {} 失败: {}", order.getOrderNo(), e.getMessage());
            }
        }
    }
}
