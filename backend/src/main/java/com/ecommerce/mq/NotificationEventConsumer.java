package com.ecommerce.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ecommerce.entity.Message;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "mq", name = "enabled", havingValue = "true")
public class NotificationEventConsumer {

    private static final String CONSUMER_NAME = "notification-consumer";

    private final MqConsumeLogService consumeLogService;
    private final MessageMapper messageMapper;
    private final ModuleSwitchService moduleSwitchService;
    private final ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    public NotificationEventConsumer(MqConsumeLogService consumeLogService,
                                     MessageMapper messageMapper,
                                     ModuleSwitchService moduleSwitchService,
                                     ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService) {
        this.consumeLogService = consumeLogService;
        this.messageMapper = messageMapper;
        this.moduleSwitchService = moduleSwitchService;
        this.managementWorkbenchRealtimeService = managementWorkbenchRealtimeService;
    }

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = RabbitMqNames.NOTIFICATION_QUEUE, containerFactory = "mqListenerContainerFactory")
    public void consume(String rawMessage) {
        DomainEvent event = JSON.parseObject(rawMessage, DomainEvent.class);
        if (event == null || !consumeLogService.tryAcquire(event.getEventId(), CONSUMER_NAME)) {
            return;
        }
        if (!moduleSwitchService.isEnabled("message")) {
            return;
        }

        JSONObject payload = event.getPayload();
        if (payload == null || payload.getLong("userId") == null) {
            return;
        }

        Message message = new Message();
        message.setUserId(payload.getLong("userId"));
        message.setTitle(payload.getString("title"));
        message.setContent(payload.getString("content"));
        message.setType(payload.getString("messageType"));
        message.setRelatedId(payload.getLong("relatedId"));
        message.setIsRead(0);
        messageMapper.insert(message);
        managementWorkbenchRealtimeService.notifyUserMessageChanged(
                message.getUserId(),
                "user-message-created"
        );
        managementWorkbenchRealtimeService.notifyMerchantMessageChanged(
                message.getUserId(),
                "merchant-message-created"
        );
    }
}
