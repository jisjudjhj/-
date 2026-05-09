package com.ecommerce.common;

import lombok.Data;

@Data
public class RiskDecision {

    private boolean allow;
    private String reasonCode;
    private Integer httpStatus;
    private String message;
    private Long retryAfterSeconds;
    private String routeId;
    private String subjectType;
    private String subjectValue;

    public static RiskDecision allow() {
        RiskDecision decision = new RiskDecision();
        decision.setAllow(true);
        return decision;
    }
}

