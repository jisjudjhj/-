package com.ecommerce.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.config.MqProperties;
import com.ecommerce.entity.MqOutboxEvent;
import com.ecommerce.mapper.MqOutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "mq", name = "enabled", havingValue = "true")
public class MqOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqOutboxPublisher.class);
    private static final String STATUS_NEW = "NEW";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_DEAD = "DEAD";

    private final MqProperties mqProperties;
    private final MqOutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;

    public MqOutboxPublisher(MqProperties mqProperties,
                            MqOutboxEventMapper outboxEventMapper,
                            RabbitTemplate rabbitTemplate) {
        this.mqProperties = mqProperties;
        this.outboxEventMapper = outboxEventMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${mq.publish-interval-ms:3000}")
    public void publishPendingEvents() {
        List<MqOutboxEvent> events = outboxEventMapper.selectList(
                new LambdaQueryWrapper<MqOutboxEvent>()
                        .in(MqOutboxEvent::getStatus, Arrays.asList(STATUS_NEW, STATUS_FAILED))
                        .le(MqOutboxEvent::getNextRetryTime, LocalDateTime.now())
                        .orderByAsc(MqOutboxEvent::getId)
                        .last("LIMIT " + Math.max(1, mqProperties.getPublisherBatchSize()))
        );

        for (MqOutboxEvent event : events) {
            publishSingleEvent(event);
        }
    }

    private void publishSingleEvent(MqOutboxEvent event) {
        try {
            DomainEvent domainEvent = new DomainEvent();
            domainEvent.setEventId(event.getEventId());
            domainEvent.setEventType(event.getEventType());
            domainEvent.setRoutingKey(event.getRoutingKey());
            domainEvent.setBizId(event.getBizId());
            domainEvent.setOccurredAt(LocalDateTime.now());
            domainEvent.setPayload(JSON.parseObject(event.getPayload(), JSONObject.class));

            rabbitTemplate.convertAndSend(
                    event.getExchangeName(),
                    event.getRoutingKey(),
                    JSON.toJSONString(domainEvent),
                    message -> {
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setMessageId(event.getEventId());
                        return message;
                    }
            );

            MqOutboxEvent update = new MqOutboxEvent();
            update.setId(event.getId());
            update.setStatus(STATUS_SENT);
            update.setSentTime(LocalDateTime.now());
            update.setErrorMessage(null);
            outboxEventMapper.updateById(update);
        } catch (Exception ex) {
            int nextRetry = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
            boolean dead = nextRetry >= Math.max(1, mqProperties.getMaxRetries());

            MqOutboxEvent update = new MqOutboxEvent();
            update.setId(event.getId());
            update.setRetryCount(nextRetry);
            update.setStatus(dead ? STATUS_DEAD : STATUS_FAILED);
            update.setNextRetryTime(dead
                    ? null
                    : LocalDateTime.now().plusSeconds(Math.max(1, mqProperties.getRetryDelaySeconds())));
            update.setErrorMessage(truncate(ex.getMessage(), 1000));
            outboxEventMapper.updateById(update);

            if (dead) {
                log.error("[MQ] outbox event moved to DEAD. eventId={}, routingKey={}, error={}",
                        event.getEventId(), event.getRoutingKey(), ex.getMessage());
            } else {
                log.warn("[MQ] outbox publish failed, will retry. eventId={}, routingKey={}, retryCount={}, error={}",
                        event.getEventId(), event.getRoutingKey(), nextRetry, ex.getMessage());
            }
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
