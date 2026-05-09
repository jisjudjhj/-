package com.ecommerce.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.Message;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "mq", name = "enabled", havingValue = "true")
public class BroadcastMessageConsumer {

    private static final String CONSUMER_NAME = "broadcast-consumer";

    private final MqConsumeLogService consumeLogService;
    private final ModuleSwitchService moduleSwitchService;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;
    private final ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    public BroadcastMessageConsumer(MqConsumeLogService consumeLogService,
                                    ModuleSwitchService moduleSwitchService,
                                    UserMapper userMapper,
                                    MessageMapper messageMapper,
                                    ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService) {
        this.consumeLogService = consumeLogService;
        this.moduleSwitchService = moduleSwitchService;
        this.userMapper = userMapper;
        this.messageMapper = messageMapper;
        this.managementWorkbenchRealtimeService = managementWorkbenchRealtimeService;
    }

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = RabbitMqNames.BROADCAST_QUEUE, containerFactory = "mqListenerContainerFactory")
    public void consume(String rawMessage) {
        DomainEvent event = JSON.parseObject(rawMessage, DomainEvent.class);
        if (event == null || !consumeLogService.tryAcquire(event.getEventId(), CONSUMER_NAME)) {
            return;
        }
        if (!moduleSwitchService.isEnabled("message")) {
            return;
        }

        JSONObject payload = event.getPayload();
        if (payload == null) {
            return;
        }

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getStatus, 1)
                        .select(User::getId, User::getRole)
        );

        for (User user : users) {
            Message message = new Message();
            message.setUserId(user.getId());
            message.setTitle(payload.getString("title"));
            message.setContent(payload.getString("content"));
            message.setType(payload.getString("messageType"));
            message.setIsRead(0);
            messageMapper.insert(message);
            managementWorkbenchRealtimeService.notifyUserMessageChanged(
                    user.getId(),
                    "user-message-created"
            );
            if (Constants.Role.MERCHANT.equalsIgnoreCase(String.valueOf(user.getRole()))) {
                managementWorkbenchRealtimeService.notifyMerchant(
                        user.getId(),
                        "merchant-message-created",
                        Collections.singletonMap("scope", "message")
                );
            }
        }
    }
}
