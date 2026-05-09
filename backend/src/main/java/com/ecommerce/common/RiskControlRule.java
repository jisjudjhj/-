package com.ecommerce.common;

import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class RiskControlRule {

    private String routeId;
    private String name;
    private String description;
    private String method;
    private String pathPattern;
    private String subjectType;
    private Integer windowSeconds;
    private Integer maxRequests;
    private Integer banThreshold;
    private Integer banDurationSeconds;
    private Integer violationWindowSeconds;
    private Boolean enabled;
    private Boolean customized;

    public RiskControlRule copy() {
        RiskControlRule copy = new RiskControlRule();
        copy.routeId = this.routeId;
        copy.name = this.name;
        copy.description = this.description;
        copy.method = this.method;
        copy.pathPattern = this.pathPattern;
        copy.subjectType = this.subjectType;
        copy.windowSeconds = this.windowSeconds;
        copy.maxRequests = this.maxRequests;
        copy.banThreshold = this.banThreshold;
        copy.banDurationSeconds = this.banDurationSeconds;
        copy.violationWindowSeconds = this.violationWindowSeconds;
        copy.enabled = this.enabled;
        copy.customized = this.customized;
        return copy;
    }

    public void normalize() {
        if (!StringUtils.hasText(routeId)) {
            routeId = "unknown_route";
        } else {
            routeId = routeId.trim();
        }
        method = StringUtils.hasText(method) ? method.trim().toUpperCase() : "GET";
        pathPattern = StringUtils.hasText(pathPattern) ? pathPattern.trim() : "/api/**";
        subjectType = StringUtils.hasText(subjectType) ? subjectType.trim().toUpperCase() : "IP";
        windowSeconds = safePositive(windowSeconds, 60);
        maxRequests = safePositive(maxRequests, 30);
        banThreshold = safePositive(banThreshold, 6);
        banDurationSeconds = safePositive(banDurationSeconds, 900);
        violationWindowSeconds = safePositive(violationWindowSeconds, 600);
        enabled = enabled == null ? Boolean.TRUE : enabled;
        customized = customized == null ? Boolean.FALSE : customized;
        if (!StringUtils.hasText(name)) {
            name = routeId;
        } else {
            name = name.trim();
        }
        if (!StringUtils.hasText(description)) {
            description = "";
        } else {
            description = description.trim();
        }
    }

    private int safePositive(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }
}

