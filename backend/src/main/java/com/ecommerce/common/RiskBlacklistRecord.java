package com.ecommerce.common;

import lombok.Data;

@Data
public class RiskBlacklistRecord {

    private String subjectType;
    private String subjectValue;
    private String reason;
    private String source;
    private String operator;
    private String routeId;
    private Boolean permanent;
    private String createdAt;
    private String expireAt;
}

