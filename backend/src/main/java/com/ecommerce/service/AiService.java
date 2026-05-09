package com.ecommerce.service;

import com.ecommerce.common.BusinessException;

import java.util.List;
import java.util.Map;

public interface AiService {

    /**
     * AI 连通性轻量测试
     * @param message 测试消息
     * @return 回复文本
     */
    String connectivityTest(String message);

    /**
     * 购物助手对话
     * @param userId 用户ID（用于获取偏好）
     * @param userMessage 用户输入
     * @param history 对话历史 [{role, content}, ...]
     * @return AI 回复文本 + 推荐商品列表
     */
    Map<String, Object> shoppingAssistant(Long userId, String userMessage, List<Map<String, String>> history);

    /**
     * 商家 AI 助手对话
     * @param merchantId 商家ID
     * @param merchantMessage 商家输入
     * @param history 对话历史
     * @param draft 当前商品草稿
     * @return 回复 + 建议动作 + 可选的商品文案草稿
     */
    default Map<String, Object> merchantAssistant(Long merchantId,
                                                  String merchantMessage,
                                                  List<Map<String, String>> history,
                                                  Map<String, Object> draft) {
        throw new BusinessException("AI 商家助手主链路未在当前分支实现");
    }

    /**
     * 商家一键生成商品上架文案
     * @param merchantId 商家ID
     * @param draft 当前商品草稿
     * @return 标题、卖点、描述、标签、客服话术等
     */
    default Map<String, Object> generateMerchantProductCopy(Long merchantId, Map<String, Object> draft) {
        throw new BusinessException("AI 商家商品文案生成主链路未在当前分支实现");
    }

    /**
     * 商品评价智能摘要
     * @param productId 商品ID
     * @return 摘要文本
     */
    String reviewSummary(Long productId);

    /**
     * 商品智能问答
     * @param productId 商品ID
     * @param question 用户问题
     * @return AI 回答文本
     */
    String productQA(Long productId, String question);

    /**
     * 客服场景 AI 回复（用户侧）
     * @param userId 用户ID
     * @param conversationContext 会话上下文（订单/商品/队列等）
     * @param history 最近对话历史
     * @param userMessage 用户消息
     * @return AI 回复文本
     */
    default String customerSupportReply(Long userId,
                                        Map<String, Object> conversationContext,
                                        List<Map<String, Object>> history,
                                        String userMessage) {
        throw new BusinessException("AI 客服回复能力未实现");
    }

    /**
     * 转人工前会话摘要（给人工客服）
     * @param userId 用户ID
     * @param conversationContext 会话上下文
     * @param transcript 会话消息
     * @return issueSummary / issueDetail / suggestedAction
     */
    default Map<String, String> summarizeCustomerServiceHandoff(Long userId,
                                                                Map<String, Object> conversationContext,
                                                                List<Map<String, Object>> transcript) {
        throw new BusinessException("AI 转人工摘要能力未实现");
    }
}
