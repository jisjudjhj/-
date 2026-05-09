package com.ecommerce.controller;

import com.ecommerce.common.Constants;
import com.ecommerce.common.RateLimit;
import com.ecommerce.common.Result;
import com.ecommerce.dto.ShoppingIntentDTO;
import com.ecommerce.service.AiService;
import com.ecommerce.service.ModuleSwitchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.model}")
    private String model;

    @Value("${ai.max-tokens:1024}")
    private int maxTokens;

    @Value("${ai.temperature:0.7}")
    private double temperature;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    /**
     * 购物助手对话（需登录）
     * body: { "message": "...", "history": [{"role":"user","content":"..."},{"role":"assistant","content":"..."}] }
     */
    @PostMapping("/chat")
    @RateLimit(key = "ai:chat", window = 60, max = 12, type = RateLimit.LimitType.USER, message = "AI 咨询过于频繁，请稍后再试")
    public Result<?> chat(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("ai-chat");
        if (!moduleSwitchService.isEnabled("ai-chat")) {
            return Result.error("购物助手功能已关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        String message = (String) body.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        Map<String, Object> result = aiService.shoppingAssistant(userId, message.trim(), history);
        sanitizeShoppingAssistantPayload(result);
        return Result.success(result);
    }

    /**
     * 商品评价智能摘要（无需登录）
     */
    @GetMapping("/review-summary/{productId}")
    public Result<?> reviewSummary(@PathVariable Long productId) {
        moduleSwitchService.requireEnabled("ai-review-summary");
        if (!moduleSwitchService.isEnabled("ai-review-summary")) {
            return Result.error("评价摘要功能已关闭");
        }
        String summary = aiService.reviewSummary(productId);
        return Result.success(summary);
    }

    /**
     * 商品智能问答（无需登录）
     * body: { "productId": 1, "question": "..." }
     */
    @PostMapping("/product-qa")
    @RateLimit(key = "ai:product-qa", window = 60, max = 40, type = RateLimit.LimitType.IP, message = "提问过于频繁，请稍后再试")
    public Result<?> productQA(@RequestBody Map<String, Object> body) {
        moduleSwitchService.requireEnabled("ai-product-qa");
        if (!moduleSwitchService.isEnabled("ai-product-qa")) {
            return Result.error("商品问答功能已关闭");
        }
        Object pidObj = body.get("productId");
        String question = (String) body.get("question");
        if (pidObj == null || question == null || question.trim().isEmpty()) {
            return Result.error("商品ID和问题不能为空");
        }
        Long productId = Long.valueOf(pidObj.toString());
        String answer = aiService.productQA(productId, question.trim());
        return Result.success(answer);
    }

    // ==================== 管理端接口 ====================

    /**
     * 获取 AI 配置信息（管理员）
     */
    @GetMapping("/admin/config")
    public Result<?> getAiConfig(HttpServletRequest request) {
        if (!Constants.Role.ADMIN.equals(request.getAttribute("role"))) {
            return Result.error(403, "无权访问");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("apiUrl", apiUrl);
        config.put("model", model);
        config.put("maxTokens", maxTokens);
        config.put("temperature", temperature);
        config.put("status", "active");

        Map<String, Boolean> modules = new LinkedHashMap<>();
        modules.put("ai-chat", moduleSwitchService.isEnabled("ai-chat"));
        modules.put("ai-merchant-copilot", moduleSwitchService.isEnabled("ai-merchant-copilot"));
        modules.put("ai-review-summary", moduleSwitchService.isEnabled("ai-review-summary"));
        modules.put("ai-product-qa", moduleSwitchService.isEnabled("ai-product-qa"));
        config.put("modules", modules);

        return Result.success(config);
    }

    /**
     * 测试 AI 连通性（管理员）
     */
    @PostMapping("/admin/test")
    public Result<?> testAi(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!Constants.Role.ADMIN.equals(request.getAttribute("role"))) {
            return Result.error(403, "无权访问");
        }
        String message = (String) body.getOrDefault("message", "你好，请用一句话介绍自己");
        try {
            long start = System.currentTimeMillis();
            String reply = aiService.connectivityTest(message);
            long elapsed = System.currentTimeMillis() - start;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reply", reply);
            data.put("responseTime", elapsed + "ms");
            data.put("status", "success");
            return Result.success(data);
        } catch (Exception e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "error");
            data.put("error", e.getMessage());
            return Result.success(data);
        }
    }

    private void sanitizeShoppingAssistantPayload(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return;
        }

        result.remove("persona");
        Object intentObject = result.get("intent");
        if (!(intentObject instanceof ShoppingIntentDTO)) {
            return;
        }

        ShoppingIntentDTO intent = (ShoppingIntentDTO) intentObject;
        intent.setSegmentCode(null);
        intent.setSegmentName(null);
        intent.setPersonaSummary(null);
        intent.setStrategyHint(null);
        intent.setTopCategories(new java.util.ArrayList<>());
        intent.setTopTags(new java.util.ArrayList<>());
    }
}
