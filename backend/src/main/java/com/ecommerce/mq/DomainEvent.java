package com.ecommerce.mq;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DomainEvent {

    private String eventId;

    private String eventType;

    private String routingKey;

    private String bizId;

    private LocalDateTime occurredAt;

    private JSONObject payload;
}
