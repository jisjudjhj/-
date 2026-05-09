package com.ecommerce.interceptor;

import com.alibaba.fastjson2.JSON;
import com.ecommerce.common.Result;
import com.ecommerce.common.RiskDecision;
import com.ecommerce.config.RiskControlProperties;
import com.ecommerce.service.RiskControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RiskControlInterceptor implements HandlerInterceptor {

    @Autowired
    private RiskControlService riskControlService;

    @Autowired
    private RiskControlProperties riskControlProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        if (StringUtils.hasText(uri) && uri.startsWith("/api/admin/risk")) {
            return true;
        }

        RiskDecision decision = riskControlService.evaluate(request);
        if (decision == null || decision.isAllow()) {
            return true;
        }

        String mode = riskControlProperties.resolveMode();
        if ("OBSERVE".equals(mode) || "WARN".equals(mode)) {
            response.setHeader("X-Risk-Warn", safeText(decision.getReasonCode(), "HIT"));
            response.setHeader("X-Risk-Route", safeText(decision.getRouteId(), "-"));
            return true;
        }

        writeBlockedResponse(response, decision);
        return false;
    }

    private void writeBlockedResponse(HttpServletResponse response, RiskDecision decision) throws Exception {
        int status = decision.getHttpStatus() == null ? 429 : decision.getHttpStatus();
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (decision.getRetryAfterSeconds() != null && decision.getRetryAfterSeconds() > 0) {
            response.setHeader("Retry-After", String.valueOf(decision.getRetryAfterSeconds()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reasonCode", decision.getReasonCode());
        data.put("routeId", decision.getRouteId());
        data.put("subjectType", decision.getSubjectType());
        data.put("subjectValue", decision.getSubjectValue());
        data.put("retryAfterSeconds", decision.getRetryAfterSeconds());

        Result<Map<String, Object>> result = new Result<>();
        result.setCode(status);
        result.setMessage(StringUtils.hasText(decision.getMessage()) ? decision.getMessage() : "请求被风控系统拦截");
        result.setData(data);
        response.getWriter().write(JSON.toJSONString(result));
    }

    private String safeText(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return value.trim();
    }
}

