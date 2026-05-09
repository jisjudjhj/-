package com.ecommerce.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ManagementRealtimePushScheduler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Value("${management.websocket.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${management.websocket.stream-refresh-interval-ms:5000}")
    public void broadcastAdminStreamRefresh() {
        if (!enabled) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/admin/stream/refresh", buildPayload("admin-stream-refresh"));
    }

    private Map<String, Object> buildPayload(String event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("timestamp", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        return payload;
    }
}
