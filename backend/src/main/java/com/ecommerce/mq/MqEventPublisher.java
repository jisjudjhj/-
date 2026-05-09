package com.ecommerce.mq;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ecommerce.config.MqProperties;
import com.ecommerce.entity.MqOutboxEvent;
import com.ecommerce.mapper.MqOutboxEventMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MqEventPublisher {

    private static final String STATUS_NEW = "NEW";

    private final MqProperties mqProperties;
    private final MqOutboxEventMapper outboxEventMapper;

    public MqEventPublisher(MqProperties mqProperties, MqOutboxEventMapper outboxEventMapper) {
        this.mqProperties = mqProperties;
        this.outboxEventMapper = outboxEventMapper;
    }

    public boolean isEnabled() {
        return mqProperties.isEnabled();
    }

    public void publishEvent(String routingKey, String bizId, JSONObject payload) {
        publish(RabbitMqNames.EVENT_EXCHANGE, routingKey, bizId, payload);
    }

    public void publishDelay(String routingKey, String bizId, JSONObject payload) {
        publish(RabbitMqNames.DELAY_EXCHANGE, routingKey, bizId, payload);
    }

    public void publish(String exchangeName, String routingKey, String bizId, JSONObject payload) {
        if (!isEnabled()) {
            return;
        }

        MqOutboxEvent event = new MqOutboxEvent();
        event.setEventId(IdUtil.simpleUUID());
        event.setEventType(routingKey);
        event.setExchangeName(exchangeName);
        event.setRoutingKey(routingKey);
        event.setBizId(bizId);
        event.setPayload(JSON.toJSONString(payload));
        event.setStatus(STATUS_NEW);
        event.setRetryCount(0);
        event.setNextRetryTime(LocalDateTime.now());
        outboxEventMapper.insert(event);
    }
}
