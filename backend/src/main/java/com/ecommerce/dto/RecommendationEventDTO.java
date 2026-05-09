package com.ecommerce.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecommendationEventDTO {

    private String eventType;

    private Long productId;

    private String scene;

    private String traceId;

    private String recommendationToken;

    private String experimentGroup;

    private Integer duration;

    private Long orderId;

    private BigDecimal amount;

    private LocalDateTime eventTime;

    private String metadata;
}
