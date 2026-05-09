package com.ecommerce.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.RateLimit;
import com.ecommerce.common.Result;
import com.ecommerce.entity.ImConversation;
import com.ecommerce.entity.ImMessage;
import com.ecommerce.entity.ImSupportAgent;
import com.ecommerce.entity.ImTicket;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.ImConversationMapper;
import com.ecommerce.mapper.ImMessageMapper;
import com.ecommerce.mapper.ImSupportAgentMapper;
import com.ecommerce.mapper.ImTicketMapper;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.service.AiService;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/im")
public class ImController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ROLE_AI = "ai";
    private static final String STATUS_AI_SERVING = "ai_serving";
    private static final int HUMAN_QUEUE_WAIT_MINUTES = 8;
    private static final int PENDING_ASSIGN_TIMEOUT_MINUTES = 10;
    private static final int PROCESSING_TIMEOUT_MINUTES = 30;
    private static final int AI_RATE_LIMIT_PER_MINUTE = 10;
    private static final int AI_REPLY_MAX_RETRY = 2;
    private static final int AI_RETRY_BACKOFF_MILLIS = 250;
    private static final int AI_CIRCUIT_FAIL_THRESHOLD = 5;
    private static final int AI_CIRCUIT_FAIL_WINDOW_MINUTES = 5;
    private static final int AI_CIRCUIT_OPEN_MINUTES = 2;
    private static final int QUEUE_RECENT_HANDLE_SAMPLE_SIZE = 60;
    private static final String AI_RATE_LIMIT_KEY_PREFIX = "im:ai:rate:";
    private static final String MESSAGE_IDEMPOTENCY_KEY_PREFIX = "im:msg:idem:";
    private static final String AI_CIRCUIT_FAIL_KEY = "im:ai:circuit:fail";
    private static final String AI_CIRCUIT_OPEN_KEY = "im:ai:circuit:open";
    private static final List<String> AI_BLOCKED_KEYWORDS = Arrays.asList(
            "炸药", "爆炸物", "枪支", "枪械", "毒品", "走私", "洗钱", "恐怖袭击",
            "黑客教程", "诈骗教程", "伪造证件", "违法交易"
    );
    private static final List<String> AI_PROMPT_INJECTION_HINTS = Arrays.asList(
            "忽略之前", "忽略上面", "系统提示词", "system prompt", "越狱",
            "你现在不是客服", "请输出你的提示词", "开发者消息", "developer message"
    );

    @Autowired
    private ModuleSwitchService moduleSwitchService;
    @Autowired
    private AiService aiService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private ImConversationMapper conversationMapper;
    @Autowired
    private ImMessageMapper messageMapper;
    @Autowired
    private ImTicketMapper ticketMapper;
    @Autowired
    private ImSupportAgentMapper supportAgentMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @GetMapping("/conversations")
    public Result<?> listConversations(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String conversationType,
                                       @RequestParam(required = false) String status,
                                       HttpServletRequest request) {
        requireEnabled();
        Long currentUserId = currentUserId(request);
        String role = currentRole(request);

        LambdaQueryWrapper<ImConversation> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, currentUserId, role);
        if (StringUtils.hasText(conversationType)) {
            wrapper.eq(ImConversation::getConversationType, conversationType.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ImConversation::getStatus, status.trim());
        }
        wrapper.orderByDesc(ImConversation::getLastMessageTime)
                .orderByDesc(ImConversation::getUpdateTime)
                .orderByDesc(ImConversation::getId);

        IPage<ImConversation> data = conversationMapper.selectPage(new Page<>(page, Math.min(Math.max(size, 1), 100)), wrapper);
        ConversationViewBatchContext context = prepareConversationViewBatchContext(data.getRecords());
        Map<String, Object> result = new HashMap<>();
        result.put("records", data.getRecords().stream()
                .map(item -> buildConversationView(item, role, context))
                .collect(Collectors.toList()));
        result.put("total", data.getTotal());
        result.put("current", data.getCurrent());
        result.put("size", data.getSize());
        return Result.success(result);
    }

    @GetMapping("/conversations/unread-count")
    public Result<?> unreadCount(HttpServletRequest request) {
        requireEnabled();
        Long currentUserId = currentUserId(request);
        String role = currentRole(request);
        LambdaQueryWrapper<ImConversation> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, currentUserId, role);
        List<ImConversation> records = conversationMapper.selectList(wrapper);
        int count = records.stream().mapToInt(item -> getUnreadCount(item, role)).sum();
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("conversationCount", records.size());
        return Result.success(result);
    }

    @GetMapping("/conversations/{id}")
    public Result<?> conversationDetail(@PathVariable Long id, HttpServletRequest request) {
        requireEnabled();
        ImConversation conversation = requireConversation(id, currentUserId(request), currentRole(request));
        return Result.success(buildConversationView(conversation, currentRole(request)));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<?> conversationMessages(@PathVariable Long id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "50") int size,
                                          HttpServletRequest request) {
        requireEnabled();
        ImConversation conversation = requireConversation(id, currentUserId(request), currentRole(request));
        IPage<ImMessage> data = messageMapper.selectPage(
                new Page<>(page, Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<ImMessage>()
                        .eq(ImMessage::getConversationId, conversation.getId())
                        .orderByAsc(ImMessage::getId)
        );
        Map<Long, User> senderUsers = loadSenderUsers(data.getRecords());
        Map<String, Object> result = new HashMap<>();
        result.put("records", data.getRecords().stream()
                .map(item -> buildMessageView(item, senderUsers))
                .collect(Collectors.toList()));
        result.put("total", data.getTotal());
        result.put("current", data.getCurrent());
        result.put("size", data.getSize());
        return Result.success(result);
    }

    @PostMapping("/conversations/open-merchant")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> openMerchantConversation(@RequestBody Map<String, Object> payload,
                                              HttpServletRequest request) {
        requireEnabled();
        Long currentUserId = currentUserId(request);
        ensureRole(currentRole(request), Constants.Role.USER);

        Long productId = parseLong(payload.get("productId"));
        Long orderId = parseLong(payload.get("orderId"));
        Long merchantId = parseLong(payload.get("merchantId"));

        Product product = productId == null ? null : productMapper.selectById(productId);
        if (productId != null && product == null) {
            throw new BusinessException("商品不存在");
        }
        Order order = orderId == null ? null : orderMapper.selectById(orderId);
        if (orderId != null && (order == null || !Objects.equals(order.getUserId(), currentUserId))) {
            throw new BusinessException("订单不存在");
        }
        if (merchantId == null && product != null) {
            merchantId = product.getMerchantId();
        }
        if (merchantId == null && order != null) {
            merchantId = resolveMerchantIdByOrder(order.getId());
        }
        if (merchantId == null) {
            throw new BusinessException("未找到商家");
        }

        ImConversation conversation = findMerchantConversation(currentUserId, merchantId, orderId, productId);
        if (conversation == null) {
            conversation = new ImConversation();
            conversation.setConversationNo(generateNo("M"));
            conversation.setConversationType("merchant");
            conversation.setUserId(currentUserId);
            conversation.setMerchantId(merchantId);
            conversation.setOrderId(orderId);
            conversation.setProductId(productId);
            conversation.setStatus("open");
            conversation.setIsEscalated(0);
            conversation.setPriority(orderId != null ? "high" : "normal");
            conversation.setUnreadUser(0);
            conversation.setUnreadMerchant(0);
            conversation.setUnreadSupport(0);
            conversationMapper.insert(conversation);
            appendSystemMessage(conversation.getId(), buildMerchantIntro(product, order, merchantId), buildMetaPayload(product, order, merchantId, null));
            refreshConversation(conversation.getId());
            conversation = conversationMapper.selectById(conversation.getId());
        }
        return Result.success(buildConversationView(conversation, Constants.Role.USER));
    }

    @PostMapping("/conversations/open-support")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> openSupportConversation(@RequestBody Map<String, Object> payload,
                                             HttpServletRequest request) {
        requireEnabled();
        Long currentUserId = currentUserId(request);
        ensureRole(currentRole(request), Constants.Role.USER);

        Long orderId = parseLong(payload.get("orderId"));
        Long productId = parseLong(payload.get("productId"));

        Order order = orderId == null ? null : orderMapper.selectById(orderId);
        if (orderId != null && (order == null || !Objects.equals(order.getUserId(), currentUserId))) {
            throw new BusinessException("订单不存在");
        }
        Product product = productId == null ? null : productMapper.selectById(productId);

        ImConversation conversation = findSupportConversation(currentUserId, orderId, productId);
        Long merchantId = product != null ? product.getMerchantId() : (order != null ? resolveMerchantIdByOrder(order.getId()) : null);

        if (conversation == null) {
            conversation = new ImConversation();
            conversation.setConversationNo(generateNo("S"));
            conversation.setConversationType("support");
            conversation.setUserId(currentUserId);
            conversation.setMerchantId(merchantId);
            conversation.setSupportAgentId(null);
            conversation.setOrderId(orderId);
            conversation.setProductId(productId);
            conversation.setStatus(STATUS_AI_SERVING);
            conversation.setIsEscalated(1);
            conversation.setPriority(orderId != null ? "urgent" : "high");
            conversation.setUnreadUser(0);
            conversation.setUnreadMerchant(0);
            conversation.setUnreadSupport(0);
            conversationMapper.insert(conversation);

            appendSystemMessage(conversation.getId(), buildAiSupportIntro(order), buildMetaPayload(product, order, merchantId, null));
            appendAiMessage(conversation.getId(), buildAiGreeting(order, product), null);
            refreshConversation(conversation.getId());
            conversation = conversationMapper.selectById(conversation.getId());
        }
        return Result.success(buildConversationView(conversation, Constants.Role.USER));
    }

    @PostMapping("/conversations/{id}/messages")
    @RateLimit(key = "im:message:send", window = 60, max = 30, type = RateLimit.LimitType.USER, message = "消息发送过于频繁，请稍后再试")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> sendMessage(@PathVariable Long id,
                                 @RequestBody Map<String, Object> payload,
                                 HttpServletRequest request) {
        requireEnabled();
        Long currentUserId = currentUserId(request);
        String role = currentRole(request);
        ImConversation conversation = requireConversation(id, currentUserId, role);

        String content = trimToNull(payload.get("content"));
        String messageType = trimToNull(payload.get("messageType"));
        String requestId = trimToNull(payload.get("requestId"));
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("消息内容不能为空");
        }
        if (!StringUtils.hasText(messageType)) {
            messageType = "text";
        }

        String idempotencyKey = buildMessageIdempotencyKey(conversation.getId(), currentUserId, role, requestId);
        if (StringUtils.hasText(idempotencyKey)) {
            ImMessage existingMessage = loadIdempotentMessage(idempotencyKey);
            if (existingMessage != null) {
                return Result.success(buildMessageView(existingMessage));
            }
            if (!Boolean.TRUE.equals(redisUtil.setIfAbsent(idempotencyKey, "PENDING", 2, TimeUnit.MINUTES))) {
                throw new BusinessException("消息发送处理中，请勿重复提交");
            }
        }

        try {
            ImMessage message = new ImMessage();
            message.setConversationId(conversation.getId());
            message.setSenderRole(role);
            message.setSenderId(currentUserId);
            message.setMessageType(messageType);
            message.setContent(content);
            message.setPayloadJson(toJson(payload.get("payload")));
            message.setIsSystem(0);
            messageMapper.insert(message);

            if (StringUtils.hasText(idempotencyKey)) {
                redisUtil.set(idempotencyKey, String.valueOf(message.getId()), 10, TimeUnit.MINUTES);
            }

            updateUnreadAfterSend(conversation, role);
            if (Constants.Role.USER.equals(role)
                    && "support".equals(conversation.getConversationType())
                    && STATUS_AI_SERVING.equals(conversation.getStatus())) {
                String aiReply = generateAiReplyWithGuards(currentUserId, conversation, content);
                appendAiMessage(conversation.getId(), aiReply, null);
                conversation = conversationMapper.selectById(conversation.getId());
            }
            refreshConversation(conversation.getId());
            Map<String, Object> realtimePayload = new LinkedHashMap<>();
            realtimePayload.put("conversationId", conversation.getId());
            realtimePayload.put("messageId", message.getId());
            publishImConversationEvent(conversation.getId(), "im-message-created", realtimePayload);
            return Result.success(buildMessageView(messageMapper.selectById(message.getId())));
        } catch (Exception ex) {
            if (StringUtils.hasText(idempotencyKey)) {
                redisUtil.delete(idempotencyKey);
            }
            throw ex;
        }
    }

    @PutMapping("/conversations/{id}/read")
    public Result<?> markRead(@PathVariable Long id, HttpServletRequest request) {
        requireEnabled();
        Long currentUserId = currentUserId(request);
        String role = currentRole(request);
        ImConversation conversation = requireConversation(id, currentUserId, role);

        ImConversation update = new ImConversation();
        update.setId(conversation.getId());
        if (Constants.Role.USER.equals(role)) {
            update.setUnreadUser(0);
        } else if (Constants.Role.MERCHANT.equals(role)) {
            update.setUnreadMerchant(0);
        } else {
            update.setUnreadSupport(0);
        }
        conversationMapper.updateById(update);
        Map<String, Object> realtimePayload = new LinkedHashMap<>();
        realtimePayload.put("conversationId", conversation.getId());
        publishImConversationEvent(conversation.getId(), "im-conversation-read", realtimePayload);
        return Result.success("已读");
    }

    @PutMapping("/conversations/{id}/status")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> updateConversationStatus(@PathVariable Long id,
                                              @RequestBody Map<String, Object> payload,
                                              HttpServletRequest request) {
        requireEnabled();
        Long currentUserId = currentUserId(request);
        String role = currentRole(request);
        ImConversation conversation = requireConversation(id, currentUserId, role);

        String status = trimToNull(payload.get("status"));
        if (!StringUtils.hasText(status)) {
            throw new BusinessException("会话状态不能为空");
        }
        if (!"open".equals(status) && !"closed".equals(status) && !"pending_support".equals(status)) {
            throw new BusinessException("不支持的会话状态");
        }
        if (Constants.Role.USER.equals(role) && "pending_support".equals(status)) {
            throw new BusinessException("当前角色无权限设置该状态");
        }

        ImConversation update = new ImConversation();
        update.setId(conversation.getId());
        update.setStatus(status);
        update.setClosedTime("closed".equals(status) ? LocalDateTime.now() : null);
        conversationMapper.updateById(update);

        String note = trimToNull(payload.get("note"));
        appendSystemMessage(
                conversation.getId(),
                StringUtils.hasText(note) ? note : ("会话状态已更新为「" + conversationStatusLabel(status) + "」。"),
                buildMetaPayload(resolveProduct(conversation.getProductId()), resolveOrder(conversation.getOrderId()), conversation.getMerchantId(), null)
        );
        refreshConversation(conversation.getId());
        Map<String, Object> realtimePayload = new LinkedHashMap<>();
        realtimePayload.put("conversationId", conversation.getId());
        realtimePayload.put("status", status);
        publishImConversationEvent(conversation.getId(), "im-conversation-status-updated", realtimePayload);
        return Result.success(buildConversationView(conversationMapper.selectById(conversation.getId()), role));
    }

    @PostMapping("/conversations/{id}/request-human-support")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> requestHumanSupport(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> payload,
                                         HttpServletRequest request) {
        requireEnabled();
        Long currentUserId = currentUserId(request);
        String role = currentRole(request);
        ensureRole(role, Constants.Role.USER);
        ImConversation conversation = requireConversation(id, currentUserId, role);
        if (!"support".equals(conversation.getConversationType())) {
            throw new BusinessException("当前会话不支持转人工");
        }
        if ("open".equals(conversation.getStatus()) && conversation.getSupportAgentId() != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("conversation", buildConversationView(conversation, role));
            result.put("queue", null);
            return Result.success(result);
        }

        ImTicket activeTicket = ticketMapper.selectOne(new LambdaQueryWrapper<ImTicket>()
                .eq(ImTicket::getConversationId, conversation.getId())
                .ne(ImTicket::getTicketStatus, "resolved")
                .orderByDesc(ImTicket::getId)
                .last("limit 1"));
        boolean createdNewTicket = false;

        if (activeTicket == null) {
            Map<String, String> handoffSummary = generateHandoffSummary(conversation, currentUserId);
            String issueSummary = StringUtils.hasText(trimToNull(payload == null ? null : payload.get("summary")))
                    ? trimToNull(payload.get("summary"))
                    : trimToNull(handoffSummary.get("issueSummary"));
            String verifiedInfo = StringUtils.hasText(trimToNull(payload == null ? null : payload.get("detail")))
                    ? trimToNull(payload.get("detail"))
                    : trimToNull(handoffSummary.get("issueDetail"));
            String suggestedAction = trimToNull(handoffSummary.get("suggestedAction"));
            String issueDetail = buildStructuredHandoffDetail(issueSummary, verifiedInfo, suggestedAction);
            if (!StringUtils.hasText(issueDetail)) {
                issueDetail = verifiedInfo;
            }

            activeTicket = new ImTicket();
            activeTicket.setConversationId(conversation.getId());
            activeTicket.setTicketNo(generateNo("TK"));
            activeTicket.setTicketStatus("pending_assign");
            activeTicket.setSourceType("ai_transfer");
            activeTicket.setIssueType(StringUtils.hasText(trimToNull(payload == null ? null : payload.get("issueType")))
                    ? trimToNull(payload.get("issueType")) : "manual_support");
            activeTicket.setIssueSummary(StringUtils.hasText(issueSummary) ? issueSummary : "用户请求转人工客服");
            activeTicket.setIssueDetail(issueDetail);
            activeTicket.setCreatedByUserId(currentUserId);
            ticketMapper.insert(activeTicket);
            createdNewTicket = true;
        }

        ImConversation update = new ImConversation();
        update.setId(conversation.getId());
        update.setStatus("pending_support");
        update.setPriority("urgent");
        update.setIsEscalated(1);
        update.setSupportAgentId(null);
        update.setClosedTime(null);
        conversationMapper.updateById(update);

        Map<String, Object> queue = buildQueueInfo(conversation.getId());
        int position = queue == null ? 1 : Number.class.cast(queue.get("position")).intValue();
        ImTicket latestTicket = ticketMapper.selectById(activeTicket.getId());
        if (createdNewTicket && latestTicket != null) {
            String handoffMessage = buildAiHandoffSystemMessage(latestTicket);
            if (StringUtils.hasText(handoffMessage)) {
                appendSystemMessage(
                        conversation.getId(),
                        handoffMessage,
                        buildMetaPayload(resolveProduct(conversation.getProductId()), resolveOrder(conversation.getOrderId()), conversation.getMerchantId(), latestTicket)
                );
            }
        }
        appendSystemMessage(
                conversation.getId(),
                "已转人工，当前排队第 " + position + " 位。补充信息会同步给客服。",
                buildMetaPayload(resolveProduct(conversation.getProductId()), resolveOrder(conversation.getOrderId()), conversation.getMerchantId(), latestTicket == null ? ticketMapper.selectById(activeTicket.getId()) : latestTicket)
        );
        refreshConversation(conversation.getId());
        Map<String, Object> realtimePayload = new LinkedHashMap<>();
        realtimePayload.put("conversationId", conversation.getId());
        realtimePayload.put("ticketId", activeTicket.getId());
        realtimePayload.put("status", "pending_support");
        publishImConversationEvent(conversation.getId(), "im-ticket-updated", realtimePayload);

        Map<String, Object> result = new LinkedHashMap<>();
        ImConversation latestConversation = conversationMapper.selectById(conversation.getId());
        result.put("conversation", buildConversationView(latestConversation, role));
        result.put("queue", buildQueueInfo(conversation.getId()));
        return Result.success(result);
    }

    @PostMapping("/conversations/{id}/escalate")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> escalate(@PathVariable Long id,
                              @RequestBody Map<String, Object> payload,
                              HttpServletRequest request) {
        requireEnabled();
        Long currentUserId = currentUserId(request);
        String role = currentRole(request);
        ImConversation conversation = requireConversation(id, currentUserId, role);
        if (!Objects.equals(conversation.getUserId(), currentUserId) && !Constants.Role.ADMIN.equals(role)) {
            throw new BusinessException("仅用户可申请平台介入");
        }

        ImSupportAgent agent = pickSupportAgent();
        ImConversation update = new ImConversation();
        update.setId(conversation.getId());
        update.setConversationType("support");
        update.setIsEscalated(1);
        update.setPriority("urgent");
        update.setStatus(agent != null ? "open" : "pending_support");
        if (agent != null) {
            update.setSupportAgentId(agent.getUserId());
        }
        conversationMapper.updateById(update);

        ImTicket ticket = new ImTicket();
        ticket.setConversationId(conversation.getId());
        ticket.setTicketNo(generateNo("TK"));
        ticket.setTicketStatus(agent != null ? "processing" : "pending_assign");
        ticket.setSourceType("merchant_escalation");
        ticket.setIssueType(StringUtils.hasText(trimToNull(payload.get("issueType"))) ? trimToNull(payload.get("issueType")) : "dispute");
        ticket.setIssueSummary(StringUtils.hasText(trimToNull(payload.get("summary"))) ? trimToNull(payload.get("summary")) : "用户申请平台介入");
        ticket.setIssueDetail(trimToNull(payload.get("detail")));
        ticket.setCreatedByUserId(currentUserId);
        ticket.setAssignedSupportId(agent != null ? agent.getUserId() : null);
        ticket.setAssignedTime(agent != null ? LocalDateTime.now() : null);
        ticketMapper.insert(ticket);

        appendSystemMessage(
                conversation.getId(),
                agent != null ? ("平台客服 " + displaySupportName(agent) + " 已介入，可继续沟通。") : "已申请平台介入，客服将尽快接入。",
                buildMetaPayload(resolveProduct(conversation.getProductId()), resolveOrder(conversation.getOrderId()), conversation.getMerchantId(), ticket)
        );
        refreshConversation(conversation.getId());
        Map<String, Object> realtimePayload = new LinkedHashMap<>();
        realtimePayload.put("conversationId", conversation.getId());
        realtimePayload.put("ticketId", ticket.getId());
        realtimePayload.put("status", update.getStatus());
        publishImConversationEvent(conversation.getId(), "im-ticket-updated", realtimePayload);
        return Result.success(buildConversationView(conversationMapper.selectById(conversation.getId()), role));
    }

    @GetMapping("/support/tickets")
    public Result<?> listTickets(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) String ticketStatus,
                                 HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);

        LambdaQueryWrapper<ImTicket> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(ticketStatus)) {
            wrapper.eq(ImTicket::getTicketStatus, ticketStatus.trim());
        }
        wrapper.orderByAsc(ImTicket::getResolvedTime).orderByDesc(ImTicket::getCreateTime);

        IPage<ImTicket> data = ticketMapper.selectPage(new Page<>(page, Math.min(Math.max(size, 1), 100)), wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("records", data.getRecords().stream().map(this::buildTicketView).collect(Collectors.toList()));
        result.put("total", data.getTotal());
        result.put("current", data.getCurrent());
        result.put("size", data.getSize());
        return Result.success(result);
    }

    @GetMapping("/support/tickets/{id}")
    public Result<?> ticketDetail(@PathVariable Long id, HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        ImTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        return Result.success(buildTicketView(ticket));
    }

    @PostMapping("/support/tickets")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> createTicket(@RequestBody Map<String, Object> payload,
                                  HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        Long currentUserId = currentUserId(request);

        Long conversationId = parseLong(payload.get("conversationId"));
        if (conversationId == null) {
            throw new BusinessException("会话ID不能为空");
        }
        ImConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException("会话不存在");
        }

        ImTicket ticket = new ImTicket();
        ticket.setConversationId(conversationId);
        ticket.setTicketNo(generateNo("T"));
        ticket.setSourceType(StringUtils.hasText(trimToNull(payload.get("sourceType"))) ? trimToNull(payload.get("sourceType")) : "manual_create");
        ticket.setIssueType(StringUtils.hasText(trimToNull(payload.get("issueType"))) ? trimToNull(payload.get("issueType")) : "manual_support");
        ticket.setIssueSummary(StringUtils.hasText(trimToNull(payload.get("issueSummary"))) ? trimToNull(payload.get("issueSummary")) : "人工创建工单");
        ticket.setIssueDetail(trimToNull(payload.get("issueDetail")));
        ticket.setCreatedByUserId(currentUserId);

        Long assignedSupportId = parseLong(payload.get("assignedSupportId"));
        if (assignedSupportId != null) {
            requireEnabledSupportAgent(assignedSupportId);
        }
        String ticketStatus = trimToNull(payload.get("ticketStatus"));
        if (!StringUtils.hasText(ticketStatus)) {
            ticketStatus = assignedSupportId != null ? "processing" : "pending_assign";
        }
        validateTicketStatus(ticketStatus);
        if ("processing".equals(ticketStatus) && assignedSupportId == null) {
            assignedSupportId = currentUserId;
            requireEnabledSupportAgent(assignedSupportId);
        }
        ticket.setTicketStatus(ticketStatus);
        ticket.setAssignedSupportId(assignedSupportId);
        if (assignedSupportId != null) {
            ticket.setAssignedTime(LocalDateTime.now());
        }
        if ("resolved".equals(ticketStatus)) {
            ticket.setResolvedById(currentUserId);
            ticket.setResolvedTime(LocalDateTime.now());
        }

        ticketMapper.insert(ticket);
        syncConversationByTicket(ticket, "已创建新工单。");
        Map<String, Object> realtimePayload = new LinkedHashMap<>();
        realtimePayload.put("conversationId", ticket.getConversationId());
        realtimePayload.put("ticketId", ticket.getId());
        realtimePayload.put("status", ticket.getTicketStatus());
        publishImConversationEvent(ticket.getConversationId(), "im-ticket-updated", realtimePayload);
        return Result.success(buildTicketView(ticketMapper.selectById(ticket.getId())));
    }

    @PutMapping("/support/tickets/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> updateTicket(@PathVariable Long id,
                                  @RequestBody Map<String, Object> payload,
                                  HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        Long currentUserId = currentUserId(request);

        ImTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }

        boolean changed = false;
        if (payload.containsKey("sourceType")) {
            ticket.setSourceType(trimToNull(payload.get("sourceType")));
            changed = true;
        }
        if (payload.containsKey("issueType")) {
            ticket.setIssueType(trimToNull(payload.get("issueType")));
            changed = true;
        }
        if (payload.containsKey("issueSummary")) {
            ticket.setIssueSummary(trimToNull(payload.get("issueSummary")));
            changed = true;
        }
        if (payload.containsKey("issueDetail")) {
            ticket.setIssueDetail(trimToNull(payload.get("issueDetail")));
            changed = true;
        }

        boolean assignedTouched = false;
        if (payload.containsKey("assignedSupportId")) {
            Long assignedSupportId = parseLong(payload.get("assignedSupportId"));
            if (assignedSupportId != null) {
                requireEnabledSupportAgent(assignedSupportId);
            }
            ticket.setAssignedSupportId(assignedSupportId);
            ticket.setAssignedTime(assignedSupportId == null ? null : LocalDateTime.now());
            changed = true;
            assignedTouched = true;
        }

        if (payload.containsKey("ticketStatus")) {
            String ticketStatus = trimToNull(payload.get("ticketStatus"));
            validateTicketStatus(ticketStatus);
            ticket.setTicketStatus(ticketStatus);
            if ("resolved".equals(ticketStatus)) {
                ticket.setResolvedById(currentUserId);
                ticket.setResolvedTime(LocalDateTime.now());
            } else {
                ticket.setResolvedById(null);
                ticket.setResolvedTime(null);
                if ("processing".equals(ticketStatus) && ticket.getAssignedSupportId() == null) {
                    requireEnabledSupportAgent(currentUserId);
                    ticket.setAssignedSupportId(currentUserId);
                    ticket.setAssignedTime(LocalDateTime.now());
                }
            }
            changed = true;
        } else if (assignedTouched && !"resolved".equals(ticket.getTicketStatus())) {
            ticket.setTicketStatus(ticket.getAssignedSupportId() == null ? "pending_assign" : "processing");
        }

        if (!changed) {
            return Result.success(buildTicketView(ticket));
        }

        ticketMapper.updateById(ticket);
        syncConversationByTicket(ticket, trimToNull(payload.get("note")));
        Map<String, Object> realtimePayload = new LinkedHashMap<>();
        realtimePayload.put("conversationId", ticket.getConversationId());
        realtimePayload.put("ticketId", ticket.getId());
        realtimePayload.put("status", ticket.getTicketStatus());
        publishImConversationEvent(ticket.getConversationId(), "im-ticket-updated", realtimePayload);
        return Result.success(buildTicketView(ticketMapper.selectById(ticket.getId())));
    }

    @DeleteMapping("/support/tickets/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> deleteTicket(@PathVariable Long id,
                                  @RequestParam(defaultValue = "false") boolean force,
                                  HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        ImTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        if (!force && !"resolved".equals(ticket.getTicketStatus())) {
            throw new BusinessException("仅允许删除已解决工单，如需强制删除请传 force=true");
        }

        Long conversationId = ticket.getConversationId();
        ticketMapper.deleteById(id);

        if (conversationId != null) {
            ImTicket latest = ticketMapper.selectOne(new LambdaQueryWrapper<ImTicket>()
                    .eq(ImTicket::getConversationId, conversationId)
                    .orderByDesc(ImTicket::getId)
                    .last("limit 1"));
            if (latest != null) {
                syncConversationByTicket(latest, "历史工单已删除，当前工单状态已同步。");
            } else {
                refreshConversation(conversationId);
            }
        }
        Map<String, Object> realtimePayload = new LinkedHashMap<>();
        realtimePayload.put("conversationId", conversationId);
        realtimePayload.put("ticketId", id);
        realtimePayload.put("status", "deleted");
        publishImConversationEvent(conversationId, "im-ticket-updated", realtimePayload);
        return Result.success("删除成功");
    }

    @GetMapping("/support/metrics")
    public Result<?> supportMetrics(HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);

        List<ImConversation> supportConversations = conversationMapper.selectList(
                new LambdaQueryWrapper<ImConversation>()
                        .eq(ImConversation::getConversationType, "support")
                        .orderByDesc(ImConversation::getId)
        );
        List<ImTicket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<ImTicket>().orderByDesc(ImTicket::getId));

        Set<Long> transferredConversationIds = tickets.stream()
                .map(ImTicket::getConversationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        long totalSupportConversations = supportConversations.size();
        long transferredToHumanCount = transferredConversationIds.size();
        long aiResolvedCount = supportConversations.stream()
                .filter(item -> "closed".equals(item.getStatus()) && !transferredConversationIds.contains(item.getId()))
                .count();

        List<ImTicket> activeTickets = tickets.stream()
                .filter(item -> "pending_assign".equals(item.getTicketStatus()) || "processing".equals(item.getTicketStatus()))
                .collect(Collectors.toList());
        long overtimeTicketCount = activeTickets.stream().filter(this::isOvertimeTicket).count();

        List<Long> firstResponseMinutesList = new ArrayList<>();
        for (ImTicket ticket : tickets) {
            if (ticket.getConversationId() == null || ticket.getCreateTime() == null) {
                continue;
            }
            ImMessage firstAdminMessage = messageMapper.selectOne(new LambdaQueryWrapper<ImMessage>()
                    .eq(ImMessage::getConversationId, ticket.getConversationId())
                    .eq(ImMessage::getSenderRole, Constants.Role.ADMIN)
                    .ge(ImMessage::getCreateTime, ticket.getCreateTime())
                    .orderByAsc(ImMessage::getCreateTime)
                    .last("limit 1"));
            if (firstAdminMessage != null && firstAdminMessage.getCreateTime() != null) {
                long minutes = Math.max(0L, Duration.between(ticket.getCreateTime(), firstAdminMessage.getCreateTime()).toMinutes());
                firstResponseMinutesList.add(minutes);
            }
        }

        double avgFirstResponseMinutes = 0D;
        if (!firstResponseMinutesList.isEmpty()) {
            avgFirstResponseMinutes = firstResponseMinutesList.stream().mapToLong(Long::longValue).average().orElse(0D);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSupportConversations", totalSupportConversations);
        result.put("aiResolvedCount", aiResolvedCount);
        result.put("transferredToHumanCount", transferredToHumanCount);
        result.put("activeTicketCount", activeTickets.size());
        result.put("overtimeTicketCount", overtimeTicketCount);
        result.put("aiResolveRate", percent(aiResolvedCount, totalSupportConversations));
        result.put("transferRate", percent(transferredToHumanCount, totalSupportConversations));
        result.put("avgFirstResponseMinutes", Math.round(avgFirstResponseMinutes * 10D) / 10D);
        result.put("overtimeTicketRatio", percent(overtimeTicketCount, activeTickets.size()));
        return Result.success(result);
    }

    @PutMapping("/support/tickets/{id}/assign")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> assignTicket(@PathVariable Long id,
                                  @RequestBody Map<String, Object> payload,
                                  HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        Long currentUserId = currentUserId(request);

        ImTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }

        Long supportUserId = parseLong(payload.get("supportUserId"));
        if (supportUserId == null) {
            supportUserId = currentUserId;
        }
        requireEnabledSupportAgent(supportUserId);
        ticket.setAssignedSupportId(supportUserId);
        ticket.setAssignedTime(LocalDateTime.now());
        ticket.setTicketStatus("processing");
        ticketMapper.updateById(ticket);

        ImConversation conversation = conversationMapper.selectById(ticket.getConversationId());
        if (conversation != null) {
            conversation.setSupportAgentId(supportUserId);
            conversation.setStatus("open");
            conversationMapper.updateById(conversation);
            User supportUser = userMapper.selectById(supportUserId);
            appendSystemMessage(
                    conversation.getId(),
                    "官方客服 " + resolveDisplayName(supportUser) + " 已接入会话。",
                    buildMetaPayload(resolveProduct(conversation.getProductId()), resolveOrder(conversation.getOrderId()), conversation.getMerchantId(), ticket)
            );
            refreshConversation(conversation.getId());
        }
        Map<String, Object> realtimePayload = new LinkedHashMap<>();
        realtimePayload.put("conversationId", ticket.getConversationId());
        realtimePayload.put("ticketId", ticket.getId());
        realtimePayload.put("status", ticket.getTicketStatus());
        realtimePayload.put("assignedSupportId", ticket.getAssignedSupportId());
        publishImConversationEvent(ticket.getConversationId(), "im-ticket-updated", realtimePayload);
        return Result.success(buildTicketView(ticketMapper.selectById(ticket.getId())));
    }

    @PutMapping("/support/tickets/{id}/status")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> updateTicketStatus(@PathVariable Long id,
                                        @RequestBody Map<String, Object> payload,
                                        HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        Long currentUserId = currentUserId(request);

        ImTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }

        String ticketStatus = trimToNull(payload.get("ticketStatus"));
        if (!StringUtils.hasText(ticketStatus)) {
            throw new BusinessException("工单状态不能为空");
        }
        if (!"pending_assign".equals(ticketStatus) && !"processing".equals(ticketStatus) && !"resolved".equals(ticketStatus)) {
            throw new BusinessException("不支持的工单状态");
        }

        ticket.setTicketStatus(ticketStatus);
        if ("resolved".equals(ticketStatus)) {
            ticket.setResolvedById(currentUserId);
            ticket.setResolvedTime(LocalDateTime.now());
        } else {
            ticket.setResolvedById(null);
            ticket.setResolvedTime(null);
            if ("processing".equals(ticketStatus) && ticket.getAssignedSupportId() == null) {
                requireEnabledSupportAgent(currentUserId);
                ticket.setAssignedSupportId(currentUserId);
                ticket.setAssignedTime(LocalDateTime.now());
            }
        }
        ticketMapper.updateById(ticket);

        ImConversation conversation = conversationMapper.selectById(ticket.getConversationId());
        if (conversation != null) {
            ImConversation conversationUpdate = new ImConversation();
            conversationUpdate.setId(conversation.getId());
            if ("resolved".equals(ticketStatus)) {
                conversationUpdate.setStatus("closed");
                conversationUpdate.setClosedTime(LocalDateTime.now());
            } else if ("pending_assign".equals(ticketStatus)) {
                conversationUpdate.setStatus("pending_support");
                conversationUpdate.setClosedTime(null);
            } else {
                conversationUpdate.setStatus("open");
                conversationUpdate.setClosedTime(null);
            }
            if (ticket.getAssignedSupportId() != null) {
                conversationUpdate.setSupportAgentId(ticket.getAssignedSupportId());
            }
            conversationMapper.updateById(conversationUpdate);

            ImTicket latestTicket = ticketMapper.selectById(ticket.getId());
            String note = trimToNull(payload.get("note"));
            appendSystemMessage(
                    conversation.getId(),
                    StringUtils.hasText(note) ? note : ("工单状态已更新为「" + ticketStatusLabel(ticketStatus) + "」。"),
                    buildMetaPayload(resolveProduct(conversation.getProductId()), resolveOrder(conversation.getOrderId()), conversation.getMerchantId(), latestTicket)
            );
            refreshConversation(conversation.getId());
        }
        Map<String, Object> realtimePayload = new LinkedHashMap<>();
        realtimePayload.put("conversationId", ticket.getConversationId());
        realtimePayload.put("ticketId", ticket.getId());
        realtimePayload.put("status", ticket.getTicketStatus());
        publishImConversationEvent(ticket.getConversationId(), "im-ticket-updated", realtimePayload);
        return Result.success(buildTicketView(ticketMapper.selectById(ticket.getId())));
    }

    @GetMapping("/support/agents")
    public Result<?> listSupportAgents(HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        List<Map<String, Object>> data = supportAgentMapper.selectList(
                        new LambdaQueryWrapper<ImSupportAgent>()
                                .orderByDesc(ImSupportAgent::getEnabled)
                                .orderByDesc(ImSupportAgent::getOnlineStatus)
                                .orderByAsc(ImSupportAgent::getId)
                ).stream()
                .map(this::buildSupportAgentView)
                .collect(Collectors.toList());
        return Result.success(data);
    }

    @GetMapping("/support/agents/{id}")
    public Result<?> supportAgentDetail(@PathVariable Long id, HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        ImSupportAgent agent = supportAgentMapper.selectById(id);
        if (agent == null) {
            throw new BusinessException("客服不存在");
        }
        return Result.success(buildSupportAgentView(agent));
    }

    @PostMapping("/support/agents")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> saveSupportAgent(@RequestBody Map<String, Object> payload,
                                      HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);

        Long userId = parseLong(payload.get("userId"));
        if (userId == null) {
            throw new BusinessException("客服账号不能为空");
        }
        User user = requireSupportAccount(userId);

        ImSupportAgent agent = supportAgentMapper.selectOne(
                new LambdaQueryWrapper<ImSupportAgent>().eq(ImSupportAgent::getUserId, userId).last("limit 1")
        );
        if (agent == null) {
            agent = new ImSupportAgent();
            agent.setUserId(userId);
            agent.setDisplayName(StringUtils.hasText(trimToNull(payload.get("displayName"))) ? trimToNull(payload.get("displayName")) : resolveDisplayName(user));
            agent.setAvatar(StringUtils.hasText(trimToNull(payload.get("avatar"))) ? trimToNull(payload.get("avatar")) : user.getAvatar());
            agent.setAgentType(StringUtils.hasText(trimToNull(payload.get("agentType"))) ? trimToNull(payload.get("agentType")) : "official");
            agent.setOnlineStatus(parseInteger(payload.get("onlineStatus"), 1));
            agent.setEnabled(parseInteger(payload.get("enabled"), 1));
            supportAgentMapper.insert(agent);
        } else {
            agent.setDisplayName(StringUtils.hasText(trimToNull(payload.get("displayName"))) ? trimToNull(payload.get("displayName")) : resolveDisplayName(user));
            agent.setAvatar(StringUtils.hasText(trimToNull(payload.get("avatar"))) ? trimToNull(payload.get("avatar")) : user.getAvatar());
            agent.setAgentType(StringUtils.hasText(trimToNull(payload.get("agentType"))) ? trimToNull(payload.get("agentType")) : agent.getAgentType());
            agent.setOnlineStatus(parseInteger(payload.get("onlineStatus"), agent.getOnlineStatus() == null ? 1 : agent.getOnlineStatus()));
            agent.setEnabled(parseInteger(payload.get("enabled"), agent.getEnabled() == null ? 1 : agent.getEnabled()));
            supportAgentMapper.updateById(agent);
        }
        return Result.success(buildSupportAgentView(agent));
    }

    @PutMapping("/support/agents/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> updateSupportAgent(@PathVariable Long id,
                                        @RequestBody Map<String, Object> payload,
                                        HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        ImSupportAgent agent = supportAgentMapper.selectById(id);
        if (agent == null) {
            throw new BusinessException("客服不存在");
        }

        if (payload.containsKey("userId")) {
            Long userId = parseLong(payload.get("userId"));
            if (userId == null) {
                throw new BusinessException("客服账号不能为空");
            }
            requireSupportAccount(userId);
            ImSupportAgent conflict = supportAgentMapper.selectOne(new LambdaQueryWrapper<ImSupportAgent>()
                    .eq(ImSupportAgent::getUserId, userId)
                    .last("limit 1"));
            if (conflict != null && !Objects.equals(conflict.getId(), agent.getId())) {
                throw new BusinessException("该用户已是客服账号");
            }
            agent.setUserId(userId);
        }

        User owner = requireSupportAccount(agent.getUserId());
        if (payload.containsKey("displayName")) {
            agent.setDisplayName(trimToNull(payload.get("displayName")));
        }
        if (!StringUtils.hasText(agent.getDisplayName()) && owner != null) {
            agent.setDisplayName(resolveDisplayName(owner));
        }
        if (payload.containsKey("avatar")) {
            agent.setAvatar(trimToNull(payload.get("avatar")));
        }
        if (!StringUtils.hasText(agent.getAvatar()) && owner != null) {
            agent.setAvatar(owner.getAvatar());
        }
        if (payload.containsKey("agentType")) {
            agent.setAgentType(trimToNull(payload.get("agentType")));
        }
        if (!StringUtils.hasText(agent.getAgentType())) {
            agent.setAgentType("official");
        }
        if (payload.containsKey("onlineStatus")) {
            agent.setOnlineStatus(parseInteger(payload.get("onlineStatus"), agent.getOnlineStatus() == null ? 1 : agent.getOnlineStatus()));
        }
        if (payload.containsKey("enabled")) {
            agent.setEnabled(parseInteger(payload.get("enabled"), agent.getEnabled() == null ? 1 : agent.getEnabled()));
        }
        if (agent.getOnlineStatus() == null) {
            agent.setOnlineStatus(1);
        }
        if (agent.getEnabled() == null) {
            agent.setEnabled(1);
        }

        supportAgentMapper.updateById(agent);
        return Result.success(buildSupportAgentView(supportAgentMapper.selectById(agent.getId())));
    }

    @DeleteMapping("/support/agents/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> deleteSupportAgent(@PathVariable Long id,
                                        HttpServletRequest request) {
        requireEnabled();
        ensureRole(currentRole(request), Constants.Role.ADMIN);
        ImSupportAgent agent = supportAgentMapper.selectById(id);
        if (agent == null) {
            throw new BusinessException("客服不存在");
        }

        long activeTicketCount = ticketMapper.selectCount(new LambdaQueryWrapper<ImTicket>()
                .eq(ImTicket::getAssignedSupportId, agent.getUserId())
                .ne(ImTicket::getTicketStatus, "resolved"));
        if (activeTicketCount > 0) {
            throw new BusinessException("该客服仍有处理中工单，暂不可删除");
        }

        long activeConversationCount = conversationMapper.selectCount(new LambdaQueryWrapper<ImConversation>()
                .eq(ImConversation::getSupportAgentId, agent.getUserId())
                .in(ImConversation::getStatus, "open", "pending_support", STATUS_AI_SERVING));
        if (activeConversationCount > 0) {
            throw new BusinessException("该客服仍关联活跃会话，暂不可删除");
        }

        supportAgentMapper.deleteById(id);
        return Result.success("删除成功");
    }

    private void requireEnabled() {
        moduleSwitchService.requireEnabled("message");
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return userId;
    }

    private String currentRole(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        return role == null ? "" : String.valueOf(role);
    }

    private void ensureRole(String actualRole, String expectedRole) {
        if (!Objects.equals(actualRole, expectedRole)) {
            throw new BusinessException("无权操作");
        }
    }

    private void applyScope(LambdaQueryWrapper<ImConversation> wrapper, Long currentUserId, String role) {
        if (Constants.Role.USER.equals(role)) {
            wrapper.eq(ImConversation::getUserId, currentUserId);
            return;
        }
        if (Constants.Role.MERCHANT.equals(role)) {
            wrapper.eq(ImConversation::getMerchantId, currentUserId);
            return;
        }
        if (Constants.Role.ADMIN.equals(role)) {
            return;
        }
        throw new BusinessException("无权访问");
    }

    private ImConversation requireConversation(Long id, Long currentUserId, String role) {
        ImConversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new BusinessException("会话不存在");
        }
        if (Constants.Role.USER.equals(role) && !Objects.equals(conversation.getUserId(), currentUserId)) {
            throw new BusinessException("无权访问");
        }
        if (Constants.Role.MERCHANT.equals(role) && !Objects.equals(conversation.getMerchantId(), currentUserId)) {
            throw new BusinessException("无权访问");
        }
        return conversation;
    }

    private ImConversation findMerchantConversation(Long userId, Long merchantId, Long orderId, Long productId) {
        LambdaQueryWrapper<ImConversation> wrapper = new LambdaQueryWrapper<ImConversation>()
                .eq(ImConversation::getConversationType, "merchant")
                .eq(ImConversation::getUserId, userId)
                .eq(ImConversation::getMerchantId, merchantId)
                .orderByDesc(ImConversation::getId)
                .last("limit 1");
        if (orderId != null) {
            wrapper.eq(ImConversation::getOrderId, orderId);
        }
        if (productId != null) {
            wrapper.eq(ImConversation::getProductId, productId);
        }
        return conversationMapper.selectOne(wrapper);
    }

    private ImConversation findSupportConversation(Long userId, Long orderId, Long productId) {
        LambdaQueryWrapper<ImConversation> wrapper = new LambdaQueryWrapper<ImConversation>()
                .eq(ImConversation::getConversationType, "support")
                .eq(ImConversation::getUserId, userId)
                .ne(ImConversation::getStatus, "closed")
                .orderByDesc(ImConversation::getId)
                .last("limit 1");
        if (orderId != null) {
            wrapper.eq(ImConversation::getOrderId, orderId);
        }
        if (productId != null) {
            wrapper.eq(ImConversation::getProductId, productId);
        }
        return conversationMapper.selectOne(wrapper);
    }

    private Map<String, Object> buildAiConversationContext(ImConversation conversation) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (conversation == null) {
            return context;
        }
        context.put("conversationId", conversation.getId());
        context.put("conversationType", conversation.getConversationType());
        context.put("status", conversation.getStatus());
        context.put("priority", conversation.getPriority());
        context.put("isEscalated", Integer.valueOf(1).equals(conversation.getIsEscalated()));
        context.put("context", buildContext(conversation));
        context.put("queue", buildQueueInfo(conversation.getId()));
        return context;
    }

    private List<Map<String, Object>> buildSupportHistory(Long conversationId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<ImMessage> records = messageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getConversationId, conversationId)
                .orderByDesc(ImMessage::getId)
                .last("limit " + safeLimit));
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(records);
        List<Map<String, Object>> history = new ArrayList<>();
        for (ImMessage item : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("senderRole", item.getSenderRole());
            row.put("content", item.getContent());
            row.put("messageType", item.getMessageType());
            row.put("isSystem", Integer.valueOf(1).equals(item.getIsSystem()));
            row.put("createTime", formatDateTime(item.getCreateTime()));
            history.add(row);
        }
        return history;
    }

    private Map<String, String> generateHandoffSummary(ImConversation conversation, Long userId) {
        if (!moduleSwitchService.isEnabled("ai-chat")) {
            return new LinkedHashMap<>();
        }
        try {
            return aiService.summarizeCustomerServiceHandoff(
                    userId,
                    buildAiConversationContext(conversation),
                    buildSupportHistory(conversation.getId(), 32)
            );
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String generateAiReplyWithGuards(Long userId, ImConversation conversation, String userMessage) {
        String message = trimToNull(userMessage);
        if (!StringUtils.hasText(message)) {
            return "请补充订单、物流或售后问题，我来继续处理。";
        }

        String blockedKeyword = matchBlockedKeyword(message);
        if (blockedKeyword != null) {
            return "该内容暂不支持处理，请直接描述订单、商品、物流或售后问题。";
        }
        if (containsPromptInjection(message)) {
            return "这类指令暂不支持，请直接描述订单或售后问题。";
        }
        if (isAiRateLimited(userId)) {
            return "消息较多，请稍后再发，我会继续跟进。";
        }
        if (!moduleSwitchService.isEnabled("ai-chat")) {
            return generateAiSupportReply(message, conversation);
        }
        if (isAiCircuitOpen()) {
            return "当前咨询较多，我先给你规则建议；也可直接转人工。";
        }

        Map<String, Object> context = buildAiConversationContext(conversation);
        List<Map<String, Object>> history = buildSupportHistory(conversation.getId(), 20);
        for (int attempt = 1; attempt <= AI_REPLY_MAX_RETRY; attempt++) {
            try {
                String reply = aiService.customerSupportReply(userId, context, history, message);
                String normalized = trimToNull(reply);
                if (StringUtils.hasText(normalized)) {
                    markAiCallSuccess();
                    return compactSupportBubble(normalized);
                }
                markAiCallFailure();
            } catch (Exception ignored) {
                // 降级到重试，避免用户长时间等待
                markAiCallFailure();
            }
            if (attempt < AI_REPLY_MAX_RETRY) {
                try {
                    Thread.sleep((long) AI_RETRY_BACKOFF_MILLIS * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return compactSupportBubble(generateAiSupportReply(message, conversation));
    }

    private String compactSupportBubble(String text) {
        String normalized = trimToNull(text);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        String singleLine = normalized.replaceAll("\\s+", " ").trim();
        if (singleLine.length() <= 110) {
            return singleLine;
        }
        int sentenceBreak = singleLine.lastIndexOf('。', 108);
        if (sentenceBreak >= 48) {
            return singleLine.substring(0, sentenceBreak + 1);
        }
        return singleLine.substring(0, 110) + "…";
    }

    private boolean isAiRateLimited(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        String key = AI_RATE_LIMIT_KEY_PREFIX + userId;
        Long count = redisUtil.increment(key);
        if (count != null && count == 1L) {
            redisUtil.expire(key, 1, TimeUnit.MINUTES);
        }
        return count != null && count > AI_RATE_LIMIT_PER_MINUTE;
    }

    private String matchBlockedKeyword(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String normalized = message.toLowerCase();
        for (String keyword : AI_BLOCKED_KEYWORDS) {
            String target = keyword == null ? null : keyword.toLowerCase();
            if (StringUtils.hasText(target) && normalized.contains(target)) {
                return keyword;
            }
        }
        return null;
    }

    private boolean containsPromptInjection(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase();
        String compact = normalized.replaceAll("\\s+", "");
        for (String hint : AI_PROMPT_INJECTION_HINTS) {
            if (!StringUtils.hasText(hint)) {
                continue;
            }
            String normalizedHint = hint.toLowerCase();
            if (normalized.contains(normalizedHint) || compact.contains(normalizedHint.replaceAll("\\s+", ""))) {
                return true;
            }
        }
        return false;
    }

    private String buildStructuredHandoffDetail(String demand, String verifiedInfo, String suggestedAction) {
        List<String> blocks = new ArrayList<>();
        String normalizedDemand = trimToNull(demand);
        String normalizedVerifiedInfo = trimToNull(verifiedInfo);
        String normalizedSuggestedAction = trimToNull(suggestedAction);
        if (StringUtils.hasText(normalizedDemand)) {
            blocks.add("【诉求】\n" + normalizedDemand);
        }
        if (StringUtils.hasText(normalizedVerifiedInfo)) {
            blocks.add("【已核实信息】\n" + normalizedVerifiedInfo);
        }
        if (StringUtils.hasText(normalizedSuggestedAction)) {
            blocks.add("【建议动作】\n" + normalizedSuggestedAction);
        }
        return blocks.isEmpty() ? null : String.join("\n\n", blocks);
    }

    private String buildAiHandoffSystemMessage(ImTicket ticket) {
        Map<String, Object> handoff = buildAiHandoff(ticket);
        if (handoff == null || handoff.isEmpty()) {
            return null;
        }
        String demand = trimToNull(handoff.get("demand"));
        String verifiedInfo = trimToNull(handoff.get("verifiedInfo"));
        String suggestedAction = trimToNull(handoff.get("suggestedAction"));
        return "AI 摘要已同步人工\n"
                + "结论：" + (StringUtils.hasText(suggestedAction) ? suggestedAction : "待补充") + "\n"
                + "原因：" + (StringUtils.hasText(verifiedInfo) ? verifiedInfo : "待补充") + "\n"
                + "诉求：" + (StringUtils.hasText(demand) ? demand : "待补充");
    }

    private String buildMessageIdempotencyKey(Long conversationId, Long senderId, String senderRole, String requestId) {
        if (conversationId == null || senderId == null || !StringUtils.hasText(senderRole) || !StringUtils.hasText(requestId)) {
            return null;
        }
        return MESSAGE_IDEMPOTENCY_KEY_PREFIX + conversationId + ":" + senderRole + ":" + senderId + ":" + requestId;
    }

    private ImMessage loadIdempotentMessage(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        Long messageId = parseLong(redisUtil.get(idempotencyKey));
        if (messageId == null) {
            return null;
        }
        return messageMapper.selectById(messageId);
    }

    private boolean isAiCircuitOpen() {
        return redisUtil.get(AI_CIRCUIT_OPEN_KEY) != null;
    }

    private void markAiCallSuccess() {
        redisUtil.delete(AI_CIRCUIT_FAIL_KEY);
        redisUtil.delete(AI_CIRCUIT_OPEN_KEY);
    }

    private void markAiCallFailure() {
        Long failCount = redisUtil.increment(AI_CIRCUIT_FAIL_KEY);
        if (failCount != null && failCount == 1L) {
            redisUtil.expire(AI_CIRCUIT_FAIL_KEY, AI_CIRCUIT_FAIL_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (failCount != null && failCount >= AI_CIRCUIT_FAIL_THRESHOLD) {
            redisUtil.set(AI_CIRCUIT_OPEN_KEY, "1", AI_CIRCUIT_OPEN_MINUTES, TimeUnit.MINUTES);
            redisUtil.delete(AI_CIRCUIT_FAIL_KEY);
        }
    }

    private void appendSystemMessage(Long conversationId, String content, Map<String, Object> payload) {
        ImMessage message = new ImMessage();
        message.setConversationId(conversationId);
        message.setSenderRole("system");
        message.setSenderId(0L);
        message.setMessageType("system");
        message.setContent(content);
        message.setPayloadJson(payload == null ? null : JSON.toJSONString(payload));
        message.setIsSystem(1);
        messageMapper.insert(message);
    }

    private void appendAiMessage(Long conversationId, String content, Map<String, Object> payload) {
        ImMessage message = new ImMessage();
        message.setConversationId(conversationId);
        message.setSenderRole(ROLE_AI);
        message.setSenderId(0L);
        message.setMessageType("ai");
        message.setContent(content);
        message.setPayloadJson(payload == null ? null : JSON.toJSONString(payload));
        message.setIsSystem(0);
        messageMapper.insert(message);
    }

    private void refreshConversation(Long conversationId) {
        ImMessage latest = messageMapper.selectOne(new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getConversationId, conversationId)
                .orderByDesc(ImMessage::getId)
                .last("limit 1"));
        if (latest == null) {
            return;
        }
        ImConversation update = new ImConversation();
        update.setId(conversationId);
        update.setLastMessage(latest.getContent());
        update.setLastMessageType(latest.getMessageType());
        update.setLastSenderRole(latest.getSenderRole());
        update.setLastSenderId(latest.getSenderId());
        update.setLastMessageTime(latest.getCreateTime());
        conversationMapper.updateById(update);
    }

    private void updateUnreadAfterSend(ImConversation conversation, String senderRole) {
        ImConversation update = new ImConversation();
        update.setId(conversation.getId());
        if (Constants.Role.USER.equals(senderRole)) {
            update.setUnreadUser(0);
            update.setUnreadMerchant(safeInt(conversation.getUnreadMerchant()) + 1);
            update.setUnreadSupport(Integer.valueOf(1).equals(conversation.getIsEscalated()) ? safeInt(conversation.getUnreadSupport()) + 1 : safeInt(conversation.getUnreadSupport()));
        } else if (Constants.Role.MERCHANT.equals(senderRole)) {
            update.setUnreadMerchant(0);
            update.setUnreadUser(safeInt(conversation.getUnreadUser()) + 1);
            update.setUnreadSupport(Integer.valueOf(1).equals(conversation.getIsEscalated()) ? safeInt(conversation.getUnreadSupport()) + 1 : safeInt(conversation.getUnreadSupport()));
        } else if (Constants.Role.ADMIN.equals(senderRole)) {
            update.setUnreadSupport(0);
            update.setUnreadUser(safeInt(conversation.getUnreadUser()) + 1);
            if (conversation.getMerchantId() != null) {
                update.setUnreadMerchant(safeInt(conversation.getUnreadMerchant()) + 1);
            }
        } else if (ROLE_AI.equals(senderRole)) {
            update.setUnreadUser(safeInt(conversation.getUnreadUser()) + 1);
        }
        conversationMapper.updateById(update);
    }

    private void publishImConversationEvent(Long conversationId, String event, Map<String, Object> payload) {
        if (conversationId == null || conversationId <= 0 || !StringUtils.hasText(event)) {
            return;
        }
        ImConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("scene", "im");
        extra.put("conversationId", conversationId);
        if (payload != null && !payload.isEmpty()) {
            extra.putAll(payload);
        }

        if (conversation.getUserId() != null && conversation.getUserId() > 0) {
            managementWorkbenchRealtimeService.notifyImUser(
                    conversation.getUserId(),
                    Constants.Role.USER,
                    event,
                    extra
            );
        }
        if (conversation.getMerchantId() != null && conversation.getMerchantId() > 0) {
            managementWorkbenchRealtimeService.notifyImUser(
                    conversation.getMerchantId(),
                    Constants.Role.MERCHANT,
                    event,
                    extra
            );
        }
        if (conversation.getSupportAgentId() != null && conversation.getSupportAgentId() > 0) {
            managementWorkbenchRealtimeService.notifyImUser(
                    conversation.getSupportAgentId(),
                    Constants.Role.ADMIN,
                    event,
                    extra
            );
        }
        if ("support".equals(conversation.getConversationType())) {
            managementWorkbenchRealtimeService.notifyImAdmins(event, extra);
        }
    }

    private Map<String, Object> buildConversationView(ImConversation item, String role) {
        return buildConversationView(item, role, null);
    }

    private Map<String, Object> buildConversationView(ImConversation item, String role, ConversationViewBatchContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId());
        result.put("conversationNo", item.getConversationNo());
        result.put("conversationType", item.getConversationType());
        result.put("status", item.getStatus());
        result.put("priority", item.getPriority());
        result.put("isEscalated", Integer.valueOf(1).equals(item.getIsEscalated()));
        result.put("lastMessage", item.getLastMessage());
        result.put("lastMessageType", item.getLastMessageType());
        result.put("lastSenderRole", item.getLastSenderRole());
        result.put("lastMessageTime", formatDateTime(item.getLastMessageTime()));
        result.put("unreadCount", getUnreadCount(item, role));
        result.put("counterpart", buildCounterpart(item, role, context));
        result.put("context", buildContext(item, context));
        result.put("ticket", buildLatestTicket(item.getId(), context));
        result.put("queue", buildQueueInfo(item.getId(), context));
        result.put("supportAgent", buildSupportInfo(item.getSupportAgentId(), context));
        result.put("createTime", formatDateTime(item.getCreateTime()));
        result.put("updateTime", formatDateTime(item.getUpdateTime()));
        return result;
    }

    private Map<String, Object> buildCounterpart(ImConversation item, String role) {
        return buildCounterpart(item, role, null);
    }

    private Map<String, Object> buildCounterpart(ImConversation item, String role, ConversationViewBatchContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (Constants.Role.USER.equals(role)) {
            if ("support".equals(item.getConversationType())) {
                User support = resolveUser(item.getSupportAgentId(), context);
                if (STATUS_AI_SERVING.equals(item.getStatus()) || support == null) {
                    result.put("role", ROLE_AI);
                    result.put("name", STATUS_AI_SERVING.equals(item.getStatus()) ? "AI客服" : "官方客服");
                    result.put("avatar", "");
                    result.put("subtitle", STATUS_AI_SERVING.equals(item.getStatus()) ? "智能客服 · 可转人工" : "平台官方客服");
                } else {
                    result.put("role", "support");
                    result.put("name", resolveDisplayName(support));
                    result.put("avatar", support.getAvatar());
                    result.put("subtitle", "平台官方客服");
                }
            } else {
                User merchant = resolveUser(item.getMerchantId(), context);
                result.put("role", "merchant");
                result.put("name", merchant != null ? resolveDisplayName(merchant) : "商家");
                result.put("avatar", merchant != null ? merchant.getAvatar() : "");
                result.put("subtitle", "商家客服");
            }
            return result;
        }
        User user = resolveUser(item.getUserId(), context);
        result.put("role", "user");
        result.put("name", user != null ? resolveDisplayName(user) : "用户");
        result.put("avatar", user != null ? user.getAvatar() : "");
        result.put("subtitle", "买家");
        return result;
    }

    private Map<String, Object> buildContext(ImConversation item) {
        return buildContext(item, null);
    }

    private Map<String, Object> buildContext(ImConversation item, ConversationViewBatchContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (item.getOrderId() != null) {
            Order order = resolveOrder(item.getOrderId(), context);
            if (order != null) {
                Map<String, Object> orderView = new LinkedHashMap<>();
                orderView.put("id", order.getId());
                orderView.put("orderNo", order.getOrderNo());
                orderView.put("status", order.getStatus());
                orderView.put("totalAmount", order.getTotalAmount());
                OrderItem first = resolveFirstOrderItem(order.getId(), context);
                if (first != null) {
                    orderView.put("productName", first.getProductName());
                    orderView.put("productImage", first.getProductImage());
                }
                result.put("order", orderView);
            }
        }
        if (item.getProductId() != null) {
            Product product = resolveProduct(item.getProductId(), context);
            if (product != null) {
                Map<String, Object> productView = new LinkedHashMap<>();
                productView.put("id", product.getId());
                productView.put("name", product.getName());
                productView.put("image", product.getImage());
                productView.put("price", product.getPrice());
                productView.put("merchantId", product.getMerchantId());
                result.put("product", productView);
            }
        }
        return result;
    }

    private Map<String, Object> buildLatestTicket(Long conversationId) {
        return buildLatestTicket(conversationId, null);
    }

    private Map<String, Object> buildLatestTicket(Long conversationId, ConversationViewBatchContext context) {
        if (context != null && context.latestTicketByConversationId != null) {
            ImTicket ticket = context.latestTicketByConversationId.get(conversationId);
            return ticket == null ? null : buildTicketView(ticket, context.userById);
        }
        ImTicket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<ImTicket>()
                .eq(ImTicket::getConversationId, conversationId)
                .orderByDesc(ImTicket::getId)
                .last("limit 1"));
        return ticket == null ? null : buildTicketView(ticket);
    }

    private Map<String, Object> buildSupportInfo(Long supportUserId) {
        return buildSupportInfo(supportUserId, null);
    }

    private Map<String, Object> buildSupportInfo(Long supportUserId, ConversationViewBatchContext context) {
        if (supportUserId == null) {
            return null;
        }
        User support = resolveUser(supportUserId, context);
        if (support == null) {
            return null;
        }
        ImSupportAgent agent = resolveSupportAgent(supportUserId, context);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", supportUserId);
        result.put("name", resolveDisplayName(support));
        result.put("avatar", support.getAvatar());
        result.put("onlineStatus", agent != null ? agent.getOnlineStatus() : 1);
        result.put("agentType", agent != null ? agent.getAgentType() : "official");
        return result;
    }

    private Map<String, Object> buildMessageView(ImMessage item) {
        return buildMessageView(item, null);
    }

    private Map<String, Object> buildMessageView(ImMessage item, Map<Long, User> userMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId());
        result.put("conversationId", item.getConversationId());
        result.put("senderRole", item.getSenderRole());
        result.put("senderId", item.getSenderId());
        result.put("messageType", item.getMessageType());
        result.put("content", item.getContent());
        result.put("payload", parseJson(item.getPayloadJson()));
        result.put("isSystem", Integer.valueOf(1).equals(item.getIsSystem()));
        result.put("createTime", formatDateTime(item.getCreateTime()));
        if (ROLE_AI.equals(item.getSenderRole())) {
            result.put("senderName", "AI客服");
            return result;
        }
        if (item.getSenderId() != null && item.getSenderId() > 0) {
            User user = userMap == null ? null : userMap.get(item.getSenderId());
            if (user == null) {
                user = resolveUser(item.getSenderId());
            }
            if (user != null) {
                result.put("senderName", resolveDisplayName(user));
                result.put("senderAvatar", user.getAvatar());
            }
        }
        return result;
    }

    private Map<String, Object> buildTicketView(ImTicket item) {
        return buildTicketView(item, null);
    }

    private Map<String, Object> buildTicketView(ImTicket item, Map<Long, User> userMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId());
        result.put("ticketNo", item.getTicketNo());
        result.put("ticketStatus", item.getTicketStatus());
        result.put("sourceType", item.getSourceType());
        result.put("issueType", item.getIssueType());
        result.put("issueSummary", item.getIssueSummary());
        result.put("issueDetail", item.getIssueDetail());
        result.put("conversationId", item.getConversationId());
        result.put("assignedSupportId", item.getAssignedSupportId());
        result.put("createdByUserId", item.getCreatedByUserId());
        result.put("assignedTime", formatDateTime(item.getAssignedTime()));
        result.put("resolvedTime", formatDateTime(item.getResolvedTime()));
        result.put("createTime", formatDateTime(item.getCreateTime()));
        result.put("aiHandoff", buildAiHandoff(item));
        if (item.getAssignedSupportId() != null) {
            User support = userMap == null ? null : userMap.get(item.getAssignedSupportId());
            if (support == null) {
                support = resolveUser(item.getAssignedSupportId());
            }
            result.put("assignedSupportName", support != null ? resolveDisplayName(support) : "官方客服");
        }
        return result;
    }

    private Map<String, Object> buildSupportAgentView(ImSupportAgent item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId());
        result.put("userId", item.getUserId());
        result.put("displayName", item.getDisplayName());
        result.put("avatar", item.getAvatar());
        result.put("agentType", item.getAgentType());
        result.put("onlineStatus", item.getOnlineStatus());
        result.put("enabled", item.getEnabled());
        return result;
    }

    private ImSupportAgent pickSupportAgent() {
        List<ImSupportAgent> candidates = supportAgentMapper.selectList(new LambdaQueryWrapper<ImSupportAgent>()
                .eq(ImSupportAgent::getEnabled, 1)
                .orderByDesc(ImSupportAgent::getOnlineStatus)
                .orderByAsc(ImSupportAgent::getId));
        for (ImSupportAgent candidate : candidates) {
            if (candidate == null || candidate.getUserId() == null) {
                continue;
            }
            User user = resolveUser(candidate.getUserId());
            if (user == null) {
                continue;
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                continue;
            }
            if (!Constants.Role.ADMIN.equals(user.getRole())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private String buildMerchantIntro(Product product, Order order, Long merchantId) {
        User merchant = resolveUser(merchantId);
        String merchantName = merchant != null ? resolveDisplayName(merchant) : "商家";
        if (order != null) {
            return "已接入 " + merchantName + " 客服，本次沟通已关联订单 " + order.getOrderNo() + "。你可以继续协商发货、退换、补偿等问题。";
        }
        if (product != null) {
            return "已接入 " + merchantName + " 客服，本次沟通已关联商品“" + product.getName() + "”。可直接咨询库存、规格、发货与售后。";
        }
        return "已接入 " + merchantName + " 客服，你可以直接沟通商品、订单和售后问题。";
    }

    private String buildSupportIntro(ImSupportAgent agent, ImTicket ticket, Order order) {
        String supportName = displaySupportName(agent);
        if (order != null) {
            return "官方客服 " + supportName + " 已接入，当前已生成工单 " + ticket.getTicketNo() + "，并关联订单 " + order.getOrderNo() + "。";
        }
        return "官方客服 " + supportName + " 已接入，当前已生成工单 " + ticket.getTicketNo() + "。";
    }

    private String displaySupportName(ImSupportAgent agent) {
        return agent != null && StringUtils.hasText(agent.getDisplayName()) ? agent.getDisplayName() : "官方客服";
    }

    private String conversationStatusLabel(String status) {
        if ("open".equals(status)) {
            return "处理中";
        }
        if ("closed".equals(status)) {
            return "已关闭";
        }
        if (STATUS_AI_SERVING.equals(status)) {
            return "AI接待中";
        }
        if ("pending_support".equals(status)) {
            return "待客服";
        }
        return status;
    }

    private Map<String, Object> buildQueueInfo(Long conversationId) {
        return buildQueueInfo(conversationId, null);
    }

    private Map<String, Object> buildQueueInfo(Long conversationId, ConversationViewBatchContext context) {
        if (context != null && context.queueInfoByConversationId != null) {
            return context.queueInfoByConversationId.get(conversationId);
        }
        ImTicket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<ImTicket>()
                .eq(ImTicket::getConversationId, conversationId)
                .eq(ImTicket::getTicketStatus, "pending_assign")
                .orderByDesc(ImTicket::getId)
                .last("limit 1"));
        if (ticket == null) {
            return null;
        }

        LocalDateTime createTime = ticket.getCreateTime();
        if (createTime == null) {
            return null;
        }
        long aheadCount = ticketMapper.selectCount(new LambdaQueryWrapper<ImTicket>()
                .eq(ImTicket::getTicketStatus, "pending_assign")
                .and(wrapper -> wrapper
                        .lt(ImTicket::getCreateTime, createTime)
                        .or(inner -> inner.eq(ImTicket::getCreateTime, createTime).lt(ImTicket::getId, ticket.getId()))));
        int position = (int) aheadCount + 1;
        int onlineAgentCount = getOnlineSupportAgentCount();
        double avgHandleMinutes = resolveRecentAvgHandleMinutes();
        int estimatedWaitMinutes = estimateQueueWaitMinutes(position, onlineAgentCount, avgHandleMinutes);

        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("ticketId", ticket.getId());
        queue.put("ticketNo", ticket.getTicketNo());
        queue.put("position", position);
        queue.put("aheadCount", Math.max(0, position - 1));
        queue.put("onlineAgentCount", onlineAgentCount);
        queue.put("avgHandleMinutes", Math.round(avgHandleMinutes * 10D) / 10D);
        queue.put("estimatedWaitMinutes", estimatedWaitMinutes);
        queue.put("ticketCreateTime", formatDateTime(ticket.getCreateTime()));
        return queue;
    }

    private int getOnlineSupportAgentCount() {
        List<ImSupportAgent> onlineAgents = supportAgentMapper.selectList(new LambdaQueryWrapper<ImSupportAgent>()
                .eq(ImSupportAgent::getEnabled, 1)
                .eq(ImSupportAgent::getOnlineStatus, 1));
        if (onlineAgents == null || onlineAgents.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ImSupportAgent agent : onlineAgents) {
            if (agent == null || agent.getUserId() == null) {
                continue;
            }
            User user = resolveUser(agent.getUserId());
            if (user == null) {
                continue;
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                continue;
            }
            if (!Constants.Role.ADMIN.equals(user.getRole())) {
                continue;
            }
            count++;
        }
        return count;
    }

    private double resolveRecentAvgHandleMinutes() {
        List<ImTicket> recentResolvedTickets = ticketMapper.selectList(new LambdaQueryWrapper<ImTicket>()
                .eq(ImTicket::getTicketStatus, "resolved")
                .isNotNull(ImTicket::getResolvedTime)
                .orderByDesc(ImTicket::getResolvedTime)
                .last("limit " + QUEUE_RECENT_HANDLE_SAMPLE_SIZE));
        if (recentResolvedTickets == null || recentResolvedTickets.isEmpty()) {
            return HUMAN_QUEUE_WAIT_MINUTES;
        }
        List<Long> durations = recentResolvedTickets.stream()
                .map(this::resolveTicketHandleMinutes)
                .filter(duration -> duration != null && duration > 0)
                .collect(Collectors.toList());
        if (durations.isEmpty()) {
            return HUMAN_QUEUE_WAIT_MINUTES;
        }
        double avg = durations.stream().mapToLong(Long::longValue).average().orElse((double) HUMAN_QUEUE_WAIT_MINUTES);
        return Math.max(3D, Math.min(avg, 180D));
    }

    private Long resolveTicketHandleMinutes(ImTicket ticket) {
        if (ticket == null || ticket.getResolvedTime() == null) {
            return null;
        }
        LocalDateTime start = ticket.getAssignedTime() != null ? ticket.getAssignedTime() : ticket.getCreateTime();
        if (start == null) {
            return null;
        }
        return Math.max(1L, Duration.between(start, ticket.getResolvedTime()).toMinutes());
    }

    private int estimateQueueWaitMinutes(int position, int onlineAgentCount, double avgHandleMinutes) {
        int safePosition = Math.max(1, position);
        int safeOnlineAgentCount = Math.max(1, onlineAgentCount);
        double safeAvgHandleMinutes = avgHandleMinutes > 0D ? avgHandleMinutes : HUMAN_QUEUE_WAIT_MINUTES;
        int aheadCount = Math.max(0, safePosition - 1);
        int rounds = (int) Math.ceil(aheadCount / (double) safeOnlineAgentCount);
        int estimated = (int) Math.round(rounds * safeAvgHandleMinutes);
        return Math.max(1, estimated);
    }

    private String buildAiSupportIntro(Order order) {
        if (order != null) {
            return "已接入 AI 客服，订单 " + order.getOrderNo() + " 已关联。";
        }
        return "已接入 AI 客服，可先描述问题。";
    }

    private String buildAiGreeting(Order order, Product product) {
        if (order != null) {
            return "你好，我是 AI 客服。你想处理发货、退款还是补偿问题？";
        }
        if (product != null) {
            return "你好，我是 AI 客服。可咨询商品、物流或售后问题。";
        }
        return "你好，我是 AI 客服。请直接说问题，我先帮你判断。";
    }

    private String generateAiSupportReply(String userMessage, ImConversation conversation) {
        String text = userMessage == null ? "" : userMessage.toLowerCase();
        if (text.contains("退款") || text.contains("退货") || text.contains("售后")) {
            return "已记录售后诉求。请补充订单号、问题图片和期望方案，也可直接转人工。";
        }
        if (text.contains("发货") || text.contains("物流") || text.contains("快递")) {
            return "已记录物流问题。请补充超时天数或停滞节点，我继续帮你判断。";
        }
        if (text.contains("赔偿") || text.contains("投诉") || text.contains("欺诈") || text.contains("假货")) {
            return "这类问题建议转人工处理，我已先帮你记录重点。";
        }
        if (text.contains("人工") || text.contains("真人")) {
            return "可以，点击下方“转人工”即可进入队列，记录会同步给客服。";
        }
        if (conversation.getOrderId() != null) {
            return "已记录订单问题。请补充你希望的处理结果，我给你下一步建议。";
        }
        return "已收到问题，我先给你建议；需要时也可直接转人工。";
    }

    private String ticketStatusLabel(String status) {
        if ("pending_assign".equals(status)) {
            return "待分配";
        }
        if ("processing".equals(status)) {
            return "处理中";
        }
        if ("resolved".equals(status)) {
            return "已解决";
        }
        return status;
    }

    private boolean isOvertimeTicket(ImTicket ticket) {
        return resolveTicketOvertimeMinutes(ticket) > 0;
    }

    private long resolveTicketOvertimeMinutes(ImTicket ticket) {
        if (ticket == null) {
            return 0L;
        }
        LocalDateTime start = null;
        int timeoutThresholdMinutes = 0;
        if ("pending_assign".equals(ticket.getTicketStatus())) {
            timeoutThresholdMinutes = PENDING_ASSIGN_TIMEOUT_MINUTES;
            start = ticket.getCreateTime();
        } else if ("processing".equals(ticket.getTicketStatus())) {
            timeoutThresholdMinutes = PROCESSING_TIMEOUT_MINUTES;
            start = ticket.getAssignedTime() != null ? ticket.getAssignedTime() : ticket.getCreateTime();
        }
        if (timeoutThresholdMinutes <= 0 || start == null) {
            return 0L;
        }
        long passedMinutes = Math.max(0L, Duration.between(start, LocalDateTime.now()).toMinutes());
        return Math.max(0L, passedMinutes - timeoutThresholdMinutes);
    }

    private double percent(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0D;
        }
        double value = (Math.max(0L, numerator) * 100D) / denominator;
        return Math.round(value * 100D) / 100D;
    }

    private Map<String, Object> buildAiHandoff(ImTicket ticket) {
        if (ticket == null) {
            return null;
        }
        String issueDetail = trimToNull(ticket.getIssueDetail());
        boolean aiTransfer = "ai_transfer".equals(ticket.getSourceType());
        if (!aiTransfer && !StringUtils.hasText(issueDetail)) {
            return null;
        }

        Map<String, String> sections = parseHandoffSections(issueDetail);
        if (!aiTransfer && sections.isEmpty()) {
            return null;
        }
        String demand = firstNonBlank(sections.get("诉求"), trimToNull(ticket.getIssueSummary()));
        String verifiedInfo = sections.get("已核实信息");
        String suggestedAction = sections.get("建议动作");

        if (!StringUtils.hasText(verifiedInfo) && StringUtils.hasText(issueDetail)) {
            int actionIndex = issueDetail.indexOf("AI建议处理");
            if (actionIndex >= 0) {
                verifiedInfo = trimToNull(issueDetail.substring(0, actionIndex));
                String actionPart = issueDetail.substring(actionIndex);
                suggestedAction = trimToNull(actionPart.replaceFirst("AI建议处理[:：]?\\s*", ""));
            } else if (aiTransfer) {
                verifiedInfo = issueDetail;
            }
        }

        if (!StringUtils.hasText(demand) && !StringUtils.hasText(verifiedInfo) && !StringUtils.hasText(suggestedAction)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("demand", demand);
        result.put("verifiedInfo", verifiedInfo);
        result.put("suggestedAction", suggestedAction);
        return result;
    }

    private Map<String, String> parseHandoffSections(String issueDetail) {
        Map<String, StringBuilder> buffers = new LinkedHashMap<>();
        buffers.put("诉求", new StringBuilder());
        buffers.put("已核实信息", new StringBuilder());
        buffers.put("建议动作", new StringBuilder());

        String detail = trimToNull(issueDetail);
        if (!StringUtils.hasText(detail)) {
            return Collections.emptyMap();
        }

        String currentKey = null;
        String[] lines = detail.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if ("【诉求】".equals(trimmed)) {
                currentKey = "诉求";
                continue;
            }
            if ("【已核实信息】".equals(trimmed)) {
                currentKey = "已核实信息";
                continue;
            }
            if ("【建议动作】".equals(trimmed)) {
                currentKey = "建议动作";
                continue;
            }
            if (!StringUtils.hasText(currentKey)) {
                continue;
            }
            StringBuilder buffer = buffers.get(currentKey);
            if (buffer == null) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(line == null ? "" : line);
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> entry : buffers.entrySet()) {
            String value = trimToNull(entry.getValue().toString());
            if (StringUtils.hasText(value)) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private Map<String, Object> buildMetaPayload(Product product, Order order, Long merchantId, ImTicket ticket) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (product != null) {
            Map<String, Object> productView = new LinkedHashMap<>();
            productView.put("id", product.getId());
            productView.put("name", product.getName());
            productView.put("image", product.getImage());
            productView.put("price", product.getPrice());
            payload.put("product", productView);
        }
        if (order != null) {
            Map<String, Object> orderView = new LinkedHashMap<>();
            orderView.put("id", order.getId());
            orderView.put("orderNo", order.getOrderNo());
            orderView.put("status", order.getStatus());
            orderView.put("totalAmount", order.getTotalAmount());
            payload.put("order", orderView);
        }
        if (merchantId != null) {
            User merchant = resolveUser(merchantId);
            Map<String, Object> merchantView = new LinkedHashMap<>();
            merchantView.put("merchantId", merchantId);
            merchantView.put("name", merchant != null ? resolveDisplayName(merchant) : "商家");
            merchantView.put("avatar", merchant != null ? merchant.getAvatar() : "");
            payload.put("merchant", merchantView);
        }
        if (ticket != null) {
            payload.put("ticket", buildTicketView(ticket));
        }
        return payload;
    }

    private Map<Long, User> loadSenderUsers(List<ImMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> senderIds = messages.stream()
                .map(ImMessage::getSenderId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (senderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(senderIds);
        return users.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
    }

    private ConversationViewBatchContext prepareConversationViewBatchContext(List<ImConversation> conversations) {
        ConversationViewBatchContext context = new ConversationViewBatchContext();
        if (conversations == null || conversations.isEmpty()) {
            return context;
        }

        Set<Long> conversationIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> supportUserIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();

        for (ImConversation conversation : conversations) {
            if (conversation == null) {
                continue;
            }
            if (conversation.getId() != null) {
                conversationIds.add(conversation.getId());
            }
            if (conversation.getUserId() != null) {
                userIds.add(conversation.getUserId());
            }
            if (conversation.getMerchantId() != null) {
                userIds.add(conversation.getMerchantId());
            }
            if (conversation.getSupportAgentId() != null) {
                supportUserIds.add(conversation.getSupportAgentId());
                userIds.add(conversation.getSupportAgentId());
            }
            if (conversation.getOrderId() != null) {
                orderIds.add(conversation.getOrderId());
            }
            if (conversation.getProductId() != null) {
                productIds.add(conversation.getProductId());
            }
        }

        if (!orderIds.isEmpty()) {
            List<Order> orders = orderMapper.selectBatchIds(orderIds);
            context.orderById = orders.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Order::getId, order -> order, (left, right) -> left));

            List<OrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                    .in(OrderItem::getOrderId, orderIds)
                    .orderByAsc(OrderItem::getId));
            Map<Long, OrderItem> firstItemByOrder = new HashMap<>();
            for (OrderItem orderItem : orderItems) {
                if (orderItem == null || orderItem.getOrderId() == null) {
                    continue;
                }
                if (!firstItemByOrder.containsKey(orderItem.getOrderId())) {
                    firstItemByOrder.put(orderItem.getOrderId(), orderItem);
                }
            }
            context.firstOrderItemByOrderId = firstItemByOrder;
        }

        if (!productIds.isEmpty()) {
            List<Product> products = productMapper.selectBatchIds(productIds);
            context.productById = products.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Product::getId, product -> product, (left, right) -> left));
        }

        List<ImTicket> latestTickets = new ArrayList<>();
        if (!conversationIds.isEmpty()) {
            List<ImTicket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<ImTicket>()
                    .in(ImTicket::getConversationId, conversationIds)
                    .orderByDesc(ImTicket::getId));
            Map<Long, ImTicket> latestTicketByConversation = new HashMap<>();
            for (ImTicket ticket : tickets) {
                if (ticket == null || ticket.getConversationId() == null) {
                    continue;
                }
                if (!latestTicketByConversation.containsKey(ticket.getConversationId())) {
                    latestTicketByConversation.put(ticket.getConversationId(), ticket);
                    latestTickets.add(ticket);
                    if (ticket.getAssignedSupportId() != null) {
                        supportUserIds.add(ticket.getAssignedSupportId());
                        userIds.add(ticket.getAssignedSupportId());
                    }
                }
            }
            context.latestTicketByConversationId = latestTicketByConversation;
            context.queueInfoByConversationId = buildQueueInfoBatch(latestTickets);
        }

        if (!supportUserIds.isEmpty()) {
            List<ImSupportAgent> supportAgents = supportAgentMapper.selectList(new LambdaQueryWrapper<ImSupportAgent>()
                    .in(ImSupportAgent::getUserId, supportUserIds)
                    .orderByAsc(ImSupportAgent::getId));
            Map<Long, ImSupportAgent> agentByUserId = new HashMap<>();
            for (ImSupportAgent supportAgent : supportAgents) {
                if (supportAgent == null || supportAgent.getUserId() == null) {
                    continue;
                }
                if (!agentByUserId.containsKey(supportAgent.getUserId())) {
                    agentByUserId.put(supportAgent.getUserId(), supportAgent);
                }
            }
            context.supportAgentByUserId = agentByUserId;
        }

        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            context.userById = users.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        }

        return context;
    }

    private Map<Long, Map<String, Object>> buildQueueInfoBatch(List<ImTicket> latestTickets) {
        if (latestTickets == null || latestTickets.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ImTicket> pendingLatestByConversation = new HashMap<>();
        for (ImTicket ticket : latestTickets) {
            if (ticket == null || ticket.getConversationId() == null || ticket.getId() == null) {
                continue;
            }
            if (!"pending_assign".equals(ticket.getTicketStatus())) {
                continue;
            }
            if (ticket.getCreateTime() == null) {
                continue;
            }
            pendingLatestByConversation.put(ticket.getConversationId(), ticket);
        }
        if (pendingLatestByConversation.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ImTicket> allPendingTickets = ticketMapper.selectList(new LambdaQueryWrapper<ImTicket>()
                .eq(ImTicket::getTicketStatus, "pending_assign")
                .orderByAsc(ImTicket::getCreateTime)
                .orderByAsc(ImTicket::getId));
        if (allPendingTickets == null || allPendingTickets.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Integer> positionByTicketId = new HashMap<>();
        int position = 1;
        for (ImTicket pendingTicket : allPendingTickets) {
            if (pendingTicket == null || pendingTicket.getId() == null) {
                continue;
            }
            positionByTicketId.put(pendingTicket.getId(), position++);
        }

        int onlineAgentCount = getOnlineSupportAgentCount();
        double avgHandleMinutes = resolveRecentAvgHandleMinutes();

        Map<Long, Map<String, Object>> queueInfoMap = new HashMap<>();
        for (Map.Entry<Long, ImTicket> entry : pendingLatestByConversation.entrySet()) {
            ImTicket ticket = entry.getValue();
            Integer ticketPosition = positionByTicketId.get(ticket.getId());
            if (ticketPosition == null) {
                continue;
            }
            int estimatedWaitMinutes = estimateQueueWaitMinutes(ticketPosition, onlineAgentCount, avgHandleMinutes);
            Map<String, Object> queue = new LinkedHashMap<>();
            queue.put("ticketId", ticket.getId());
            queue.put("ticketNo", ticket.getTicketNo());
            queue.put("position", ticketPosition);
            queue.put("aheadCount", Math.max(0, ticketPosition - 1));
            queue.put("onlineAgentCount", onlineAgentCount);
            queue.put("avgHandleMinutes", Math.round(avgHandleMinutes * 10D) / 10D);
            queue.put("estimatedWaitMinutes", estimatedWaitMinutes);
            queue.put("ticketCreateTime", formatDateTime(ticket.getCreateTime()));
            queueInfoMap.put(entry.getKey(), queue);
        }
        return queueInfoMap;
    }

    private Order resolveOrder(Long orderId, ConversationViewBatchContext context) {
        if (context != null && context.orderById != null) {
            Order order = context.orderById.get(orderId);
            if (order != null) {
                return order;
            }
        }
        return resolveOrder(orderId);
    }

    private Product resolveProduct(Long productId, ConversationViewBatchContext context) {
        if (context != null && context.productById != null) {
            Product product = context.productById.get(productId);
            if (product != null) {
                return product;
            }
        }
        return resolveProduct(productId);
    }

    private User resolveUser(Long userId, ConversationViewBatchContext context) {
        if (context != null && context.userById != null) {
            User user = context.userById.get(userId);
            if (user != null) {
                return user;
            }
        }
        return resolveUser(userId);
    }

    private OrderItem resolveFirstOrderItem(Long orderId, ConversationViewBatchContext context) {
        if (orderId == null) {
            return null;
        }
        if (context != null && context.firstOrderItemByOrderId != null) {
            OrderItem orderItem = context.firstOrderItemByOrderId.get(orderId);
            if (orderItem != null) {
                return orderItem;
            }
        }
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId)
                .last("limit 1"));
        return items.isEmpty() ? null : items.get(0);
    }

    private ImSupportAgent resolveSupportAgent(Long supportUserId, ConversationViewBatchContext context) {
        if (supportUserId == null) {
            return null;
        }
        if (context != null && context.supportAgentByUserId != null) {
            ImSupportAgent supportAgent = context.supportAgentByUserId.get(supportUserId);
            if (supportAgent != null) {
                return supportAgent;
            }
        }
        return supportAgentMapper.selectOne(new LambdaQueryWrapper<ImSupportAgent>()
                .eq(ImSupportAgent::getUserId, supportUserId)
                .last("limit 1"));
    }

    private static class ConversationViewBatchContext {
        private Map<Long, User> userById = Collections.emptyMap();
        private Map<Long, Order> orderById = Collections.emptyMap();
        private Map<Long, Product> productById = Collections.emptyMap();
        private Map<Long, OrderItem> firstOrderItemByOrderId = Collections.emptyMap();
        private Map<Long, ImTicket> latestTicketByConversationId = Collections.emptyMap();
        private Map<Long, Map<String, Object>> queueInfoByConversationId = Collections.emptyMap();
        private Map<Long, ImSupportAgent> supportAgentByUserId = Collections.emptyMap();
    }

    private int getUnreadCount(ImConversation item, String role) {
        if (Constants.Role.USER.equals(role)) {
            return safeInt(item.getUnreadUser());
        }
        if (Constants.Role.MERCHANT.equals(role)) {
            return safeInt(item.getUnreadMerchant());
        }
        return safeInt(item.getUnreadSupport());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private Long resolveMerchantIdByOrder(Long orderId) {
        if (orderId == null) {
            return null;
        }
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId).orderByAsc(OrderItem::getId));
        if (items.isEmpty()) {
            return null;
        }
        Product product = productMapper.selectById(items.get(0).getProductId());
        return product != null ? product.getMerchantId() : null;
    }

    private Product resolveProduct(Long productId) {
        return productId == null ? null : productMapper.selectById(productId);
    }

    private Order resolveOrder(Long orderId) {
        return orderId == null ? null : orderMapper.selectById(orderId);
    }

    private User resolveUser(Long userId) {
        return userId == null || userId <= 0 ? null : userMapper.selectById(userId);
    }

    private User requireSupportAccount(Long userId) {
        User user = resolveUser(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("客服账号已被禁用");
        }
        if (!Constants.Role.ADMIN.equals(user.getRole())) {
            throw new BusinessException("客服账号必须为管理员角色");
        }
        return user;
    }

    private ImSupportAgent requireEnabledSupportAgent(Long userId) {
        requireSupportAccount(userId);
        ImSupportAgent supportAgent = supportAgentMapper.selectOne(new LambdaQueryWrapper<ImSupportAgent>()
                .eq(ImSupportAgent::getUserId, userId)
                .last("limit 1"));
        if (supportAgent == null) {
            throw new BusinessException("指定客服未开通席位");
        }
        if (!Integer.valueOf(1).equals(supportAgent.getEnabled())) {
            throw new BusinessException("指定客服席位已禁用");
        }
        return supportAgent;
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        if (StringUtils.hasText(user.getPhone())) {
            return user.getPhone();
        }
        return "用户";
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME_FORMATTER);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            long parsed = ((Number) value).longValue();
            return parsed > 0 ? parsed : null;
        }
        try {
            long parsed = Long.parseLong(String.valueOf(value).trim());
            return parsed > 0 ? parsed : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private Integer parseInteger(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : text;
        }
        return JSON.toJSONString(value);
    }

    private void validateTicketStatus(String ticketStatus) {
        if (!StringUtils.hasText(ticketStatus)) {
            throw new BusinessException("工单状态不能为空");
        }
        if (!"pending_assign".equals(ticketStatus)
                && !"processing".equals(ticketStatus)
                && !"resolved".equals(ticketStatus)) {
            throw new BusinessException("不支持的工单状态");
        }
    }

    private void syncConversationByTicket(ImTicket ticket, String note) {
        if (ticket == null || ticket.getConversationId() == null) {
            return;
        }
        ImConversation conversation = conversationMapper.selectById(ticket.getConversationId());
        if (conversation == null) {
            return;
        }

        ImConversation update = new ImConversation();
        update.setId(conversation.getId());
        if ("resolved".equals(ticket.getTicketStatus())) {
            update.setStatus("closed");
            update.setClosedTime(LocalDateTime.now());
        } else if ("pending_assign".equals(ticket.getTicketStatus())) {
            update.setStatus("pending_support");
            update.setClosedTime(null);
            update.setSupportAgentId(null);
        } else {
            update.setStatus("open");
            update.setClosedTime(null);
            if (ticket.getAssignedSupportId() != null) {
                update.setSupportAgentId(ticket.getAssignedSupportId());
            }
        }
        conversationMapper.updateById(update);

        ImTicket latestTicket = ticketMapper.selectById(ticket.getId());
        appendSystemMessage(
                conversation.getId(),
                StringUtils.hasText(note) ? note : ("工单状态已更新为「" + ticketStatusLabel(ticket.getTicketStatus()) + "」。"),
                buildMetaPayload(resolveProduct(conversation.getProductId()), resolveOrder(conversation.getOrderId()), conversation.getMerchantId(), latestTicket)
        );
        refreshConversation(conversation.getId());
    }

    private Object parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSONObject.parse(json);
        } catch (Exception ignore) {
            return json;
        }
    }
}
