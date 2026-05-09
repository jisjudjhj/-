package com.ecommerce.controller;

import com.ecommerce.common.Log;
import com.ecommerce.common.Result;
import com.ecommerce.common.RiskBlacklistRecord;
import com.ecommerce.common.RiskControlRule;
import com.ecommerce.service.RiskControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/risk")
public class AdminRiskController {

    @Autowired
    private RiskControlService riskControlService;

    @GetMapping("/overview")
    public Result<?> overview() {
        return Result.success(riskControlService.overview());
    }

    @GetMapping("/rules")
    public Result<?> listRules() {
        List<RiskControlRule> rules = riskControlService.listRules();
        return Result.success(rules);
    }

    @PutMapping("/rules/{routeId}")
    @Log(module = "风控管理", action = "更新限流规则")
    public Result<?> updateRule(@PathVariable String routeId,
                                @RequestBody Map<String, Object> payload,
                                HttpServletRequest request) {
        RiskControlRule rule = riskControlService.updateRule(routeId, payload, resolveOperator(request));
        return Result.success("规则更新成功", rule);
    }

    @PostMapping("/rules/{routeId}/reset")
    @Log(module = "风控管理", action = "重置限流规则")
    public Result<?> resetRule(@PathVariable String routeId, HttpServletRequest request) {
        RiskControlRule rule = riskControlService.resetRule(routeId, resolveOperator(request));
        return Result.success("规则已重置为默认值", rule);
    }

    @GetMapping("/blacklist")
    public Result<?> listBlacklist(@RequestParam(required = false) String subjectType,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return Result.success(riskControlService.listBlacklist(subjectType, keyword, page, size));
    }

    @PostMapping("/blacklist")
    @Log(module = "风控管理", action = "新增黑名单")
    public Result<?> addBlacklist(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String subjectType = asString(payload.get("subjectType"));
        String subjectValue = asString(payload.get("subjectValue"));
        String reason = asString(payload.get("reason"));
        Integer durationSeconds = parseInt(payload.get("durationSeconds"));

        RiskBlacklistRecord record = riskControlService.addBlacklist(
                subjectType,
                subjectValue,
                durationSeconds,
                reason,
                "MANUAL",
                resolveOperator(request),
                asString(payload.get("routeId"))
        );
        return Result.success("已加入黑名单", record);
    }

    @DeleteMapping("/blacklist")
    @Log(module = "风控管理", action = "移除黑名单")
    public Result<?> removeBlacklist(@RequestParam String subjectType,
                                     @RequestParam String subjectValue) {
        boolean success = riskControlService.removeBlacklist(subjectType, subjectValue);
        return success ? Result.success("已移除黑名单") : Result.error("黑名单记录不存在");
    }

    private String resolveOperator(HttpServletRequest request) {
        if (request == null) {
            return "admin";
        }
        Object username = request.getAttribute("username");
        if (username != null && StringUtils.hasText(username.toString())) {
            return username.toString();
        }
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return "uid:" + userId;
        }
        return "admin";
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}

