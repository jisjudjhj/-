package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.service.AiService;
import com.ecommerce.service.ModuleSwitchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant/ai")
public class MerchantAiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @PostMapping("/chat")
    public Result<?> chat(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("ai-merchant-copilot");
        Long merchantId = (Long) request.getAttribute("userId");
        String message = body == null ? null : stringValue(body.get("message"));
        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = body == null
                ? Collections.emptyList()
                : (List<Map<String, String>>) body.get("history");

        @SuppressWarnings("unchecked")
        Map<String, Object> draft = body == null
                ? Collections.emptyMap()
                : (Map<String, Object>) body.get("draft");

        return Result.success(aiService.merchantAssistant(merchantId, message.trim(), history, draft));
    }

    @PostMapping("/product-copy")
    public Result<?> generateProductCopy(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("ai-merchant-copilot");
        Long merchantId = (Long) request.getAttribute("userId");

        @SuppressWarnings("unchecked")
        Map<String, Object> draft = body == null
                ? Collections.emptyMap()
                : (Map<String, Object>) body.getOrDefault("draft", body);

        return Result.success(aiService.generateMerchantProductCopy(merchantId, draft));
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
