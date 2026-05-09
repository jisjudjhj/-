package com.ecommerce.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "risk-control")
public class RiskControlProperties {

    /**
     * 是否启用统一风控（限流 + 黑名单）
     */
    private boolean enabled = true;

    /**
     * 风控模式：
     * BLOCK: 命中即拦截
     * WARN: 命中仅告警，不拦截
     * OBSERVE: 仅观测打点
     */
    private String mode = "BLOCK";

    /**
     * 设备标识请求头，用于设备维度黑名单
     */
    private String deviceIdHeader = "X-Device-Id";

    /**
     * 是否信任代理头获取客户端IP
     */
    private boolean trustProxyHeaders = true;

    public String resolveMode() {
        if (!StringUtils.hasText(mode)) {
            return "BLOCK";
        }
        return mode.trim().toUpperCase();
    }
}

