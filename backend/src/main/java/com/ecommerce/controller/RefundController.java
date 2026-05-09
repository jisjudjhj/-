package com.ecommerce.controller;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Log;
import com.ecommerce.common.Result;
import com.ecommerce.dto.RecommendationEventDTO;
import com.ecommerce.entity.Message;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.RefundRequest;
import com.ecommerce.entity.User;
import com.ecommerce.entity.WalletTransaction;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.RefundMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.mapper.WalletTransactionMapper;
import com.ecommerce.mq.MqEventPublisher;
import com.ecommerce.mq.RabbitMqNames;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.RecommendationAsyncService;
import com.ecommerce.utils.RefundViewUtil;
import com.ecommerce.utils.RefundViewUtil.InterventionMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private static final String INTERVENTION_PENDING = "pending";
    private static final String INTERVENTION_APPROVED = "approved";
    private static final String INTERVENTION_REJECTED = "rejected";

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private MqEventPublisher mqEventPublisher;

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @Autowired
    private RecommendationAsyncService recommendationAsyncService;

    @PostMapping
    public Result<?> applyRefund(@RequestBody RefundRequest refund, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("refund");
        if (!moduleSwitchService.isEnabled("refund")) {
            return Result.error("退款功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");

        if (refund.getOrderId() == null) {
            throw new BusinessException("订单ID不能为空");
        }
        if (refund.getReason() == null || refund.getReason().isEmpty()) {
            throw new BusinessException("退款原因不能为空");
        }

        Order order = orderMapper.selectById(refund.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != Constants.OrderStatus.PAID
                && order.getStatus() != Constants.OrderStatus.SHIPPED
                && order.getStatus() != Constants.OrderStatus.COMPLETED) {
            throw new BusinessException("当前订单状态不支持退款");
        }
        Long merchantId = resolveSingleMerchantId(order.getId());

        long existCount = refundMapper.selectCount(
                new LambdaQueryWrapper<RefundRequest>()
                        .eq(RefundRequest::getOrderId, refund.getOrderId())
                        .in(RefundRequest::getStatus,
                                Constants.RefundStatus.PENDING,
                                Constants.RefundStatus.APPROVED,
                                Constants.RefundStatus.REFUNDED));
        if (existCount > 0) {
            throw new BusinessException("该订单已有退款申请在处理中");
        }

        refund.setUserId(userId);
        refund.setAmount(order.getTotalAmount());
        refund.setStatus(Constants.RefundStatus.PENDING);
        refund.setId(null);
        refundMapper.insert(refund);

        dispatchRefundMessage(
                RabbitMqNames.ROUTING_REFUND_APPLIED,
                userId,
                "退款申请已提交",
                "您的订单 " + order.getOrderNo() + " 退款申请已提交，请等待审核",
                order.getId(),
                refund.getId(),
                order.getOrderNo(),
                refund.getAmount()
        );

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("scope", "refund");
        payload.put("refundId", refund.getId());
        payload.put("orderId", order.getId());
        payload.put("status", "pending");
        managementWorkbenchRealtimeService.notifyAdmins("refund-updated", payload);
        managementWorkbenchRealtimeService.notifyMerchant(merchantId, "refund-updated", payload);

        RefundViewUtil.enrichRefundView(refund);
        return Result.success("退款申请已提交", refund);
    }

    @GetMapping("/my")
    public Result<?> myRefunds(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int size,
                               HttpServletRequest request) {
        moduleSwitchService.requireEnabled("refund");
        if (!moduleSwitchService.isEnabled("refund")) {
            return Result.success(emptyPage(page, size));
        }
        Long userId = (Long) request.getAttribute("userId");
        IPage<RefundRequest> refundPage = refundMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<RefundRequest>()
                        .eq(RefundRequest::getUserId, userId)
                        .orderByDesc(RefundRequest::getCreateTime));
        refundPage.getRecords().forEach(refund -> {
            Order order = orderMapper.selectById(refund.getOrderId());
            if (order != null) {
                refund.setOrderNo(order.getOrderNo());
            }
            RefundViewUtil.enrichRefundView(refund);
        });
        return Result.success(refundPage);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("refund");
        if (!moduleSwitchService.isEnabled("refund")) {
            return Result.error("退款功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        RefundRequest refund = refundMapper.selectById(id);
        if (refund == null) {
            throw new BusinessException("退款记录不存在");
        }
        boolean isOwner = refund.getUserId().equals(userId);
        boolean isAdmin = Constants.Role.ADMIN.equals(role);
        boolean isMerchant = Constants.Role.MERCHANT.equals(role);
        if (isMerchant) {
            checkMerchantOwnsRefund(refund.getOrderId(), userId);
        }
        if (!isOwner && !isAdmin && !isMerchant) {
            throw new BusinessException("无权查看");
        }
        Order order = orderMapper.selectById(refund.getOrderId());
        if (order != null) {
            refund.setOrderNo(order.getOrderNo());
        }
        User user = userMapper.selectById(refund.getUserId());
        if (user != null) {
            refund.setUsername(user.getUsername());
        }
        RefundViewUtil.enrichRefundView(refund);
        return Result.success(refund);
    }

    @Log(module = "退款管理", action = "同意退款")
    @PostMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> approveRefund(@PathVariable Long id, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("refund");
        if (!moduleSwitchService.isEnabled("refund")) {
            return Result.error("退款功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        if (!Constants.Role.ADMIN.equals(role) && !Constants.Role.MERCHANT.equals(role)) {
            throw new BusinessException("无权操作");
        }

        RefundRequest refund = refundMapper.selectById(id);
        if (refund == null || refund.getStatus() != Constants.RefundStatus.PENDING) {
            throw new BusinessException("退款申请不存在或已处理");
        }

        if (Constants.Role.MERCHANT.equals(role)) {
            checkMerchantOwnsRefund(refund.getOrderId(), userId);
        }
        Long merchantId = resolveSingleMerchantId(refund.getOrderId());

        refund.setStatus(Constants.RefundStatus.REFUNDED);
        RefundRequest refundUpdate = new RefundRequest();
        refundUpdate.setStatus(Constants.RefundStatus.REFUNDED);
        int refundUpdated = refundMapper.update(refundUpdate,
                new LambdaUpdateWrapper<RefundRequest>()
                        .eq(RefundRequest::getId, id)
                        .eq(RefundRequest::getStatus, Constants.RefundStatus.PENDING));
        if (refundUpdated == 0) {
            throw new BusinessException("退款申请已被其他操作处理");
        }

        Order order = performRefundPayout(refund);

        dispatchRefundMessage(
                RabbitMqNames.ROUTING_REFUND_REFUNDED,
                refund.getUserId(),
                "退款成功",
                "您的退款申请已通过，¥" + refund.getAmount().toPlainString() + " 已退回钱包",
                refund.getOrderId(),
                refund.getId(),
                order != null ? order.getOrderNo() : null,
                refund.getAmount()
        );

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("scope", "refund");
        payload.put("refundId", refund.getId());
        payload.put("orderId", refund.getOrderId());
        payload.put("status", "refunded");
        managementWorkbenchRealtimeService.notifyAdmins("refund-updated", payload);
        managementWorkbenchRealtimeService.notifyMerchant(merchantId, "refund-updated", payload);

        return Result.success("退款已通过并打款");
    }

    @Log(module = "退款管理", action = "拒绝退款")
    @PostMapping("/{id}/reject")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> rejectRefund(@PathVariable Long id, @RequestBody RefundRequest body,
                                  HttpServletRequest request) {
        moduleSwitchService.requireEnabled("refund");
        if (!moduleSwitchService.isEnabled("refund")) {
            return Result.error("退款功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        if (!Constants.Role.ADMIN.equals(role) && !Constants.Role.MERCHANT.equals(role)) {
            throw new BusinessException("无权操作");
        }

        RefundRequest refund = refundMapper.selectById(id);
        if (refund == null || refund.getStatus() != Constants.RefundStatus.PENDING) {
            throw new BusinessException("退款申请不存在或已处理");
        }

        if (Constants.Role.MERCHANT.equals(role)) {
            checkMerchantOwnsRefund(refund.getOrderId(), userId);
        }
        Long merchantId = resolveSingleMerchantId(refund.getOrderId());

        refund.setStatus(Constants.RefundStatus.REJECTED);
        refund.setRejectReason(body.getRejectReason());
        RefundRequest refundUpdate = new RefundRequest();
        refundUpdate.setStatus(Constants.RefundStatus.REJECTED);
        refundUpdate.setRejectReason(body.getRejectReason());
        int refundUpdated = refundMapper.update(refundUpdate,
                new LambdaUpdateWrapper<RefundRequest>()
                        .eq(RefundRequest::getId, id)
                        .eq(RefundRequest::getStatus, Constants.RefundStatus.PENDING));
        if (refundUpdated == 0) {
            throw new BusinessException("退款申请已被其他操作处理");
        }

        dispatchRefundMessage(
                RabbitMqNames.ROUTING_REFUND_REJECTED,
                refund.getUserId(),
                "退款被拒绝",
                "您的退款申请被拒绝，原因：" + (body.getRejectReason() != null ? body.getRejectReason() : "无"),
                refund.getOrderId(),
                refund.getId(),
                null,
                refund.getAmount()
        );

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("scope", "refund");
        payload.put("refundId", refund.getId());
        payload.put("orderId", refund.getOrderId());
        payload.put("status", "rejected");
        managementWorkbenchRealtimeService.notifyAdmins("refund-updated", payload);
        managementWorkbenchRealtimeService.notifyMerchant(merchantId, "refund-updated", payload);

        return Result.success("已拒绝退款");
    }

    @Log(module = "退款管理", action = "申请平台介入")
    @PostMapping("/{id}/intervene")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> interveneRefund(@PathVariable Long id,
                                     @RequestBody(required = false) Map<String, Object> body,
                                     HttpServletRequest request) {
        moduleSwitchService.requireEnabled("refund");
        if (!moduleSwitchService.isEnabled("refund")) {
            return Result.error("退款功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        ensureUserRole(role);

        RefundRequest refund = refundMapper.selectById(id);
        if (refund == null || !userId.equals(refund.getUserId())) {
            throw new BusinessException("退款记录不存在");
        }
        if (refund.getStatus() != Constants.RefundStatus.REJECTED) {
            throw new BusinessException("当前退款状态不可申请平台介入");
        }
        if (RefundViewUtil.hasPendingIntervention(refund.getDescription())) {
            throw new BusinessException("该退款已在平台介入处理中");
        }

        String reason = body == null ? null : trimToNull(body.get("reason"));
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("请输入平台介入原因");
        }

        String updatedDescription = RefundViewUtil.upsertInterventionMetadata(
                refund.getDescription(),
                INTERVENTION_PENDING,
                reason,
                LocalDateTime.now()
        );
        RefundRequest update = new RefundRequest();
        update.setDescription(updatedDescription);
        refundMapper.update(update,
                new LambdaUpdateWrapper<RefundRequest>()
                        .eq(RefundRequest::getId, id)
                        .eq(RefundRequest::getUserId, userId)
                        .eq(RefundRequest::getStatus, Constants.RefundStatus.REJECTED));

        RefundRequest latest = refundMapper.selectById(id);
        enrichRefundWithContext(latest);

        Long merchantId = resolveSingleMerchantId(refund.getOrderId());
        dispatchRefundMessage(
                RabbitMqNames.ROUTING_REFUND_APPLIED,
                refund.getUserId(),
                "平台介入申请已提交",
                "您的退款争议已提交平台介入，请等待平台裁定",
                refund.getOrderId(),
                refund.getId(),
                latest.getOrderNo(),
                refund.getAmount()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "refund");
        payload.put("refundId", refund.getId());
        payload.put("orderId", refund.getOrderId());
        payload.put("status", "intervention_pending");
        managementWorkbenchRealtimeService.notifyAdmins("refund-updated", payload);
        managementWorkbenchRealtimeService.notifyMerchant(merchantId, "refund-updated", payload);

        return Result.success("已提交平台介入申请", latest);
    }

    @Log(module = "退款管理", action = "平台裁定退款")
    @PostMapping("/{id}/resolve-intervention")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> resolveIntervention(@PathVariable Long id,
                                         @RequestBody Map<String, Object> body,
                                         HttpServletRequest request) {
        moduleSwitchService.requireEnabled("refund");
        if (!moduleSwitchService.isEnabled("refund")) {
            return Result.error("退款功能暂时关闭");
        }
        String role = (String) request.getAttribute("role");
        if (!Constants.Role.ADMIN.equals(role)) {
            throw new BusinessException("无权操作");
        }

        RefundRequest refund = refundMapper.selectById(id);
        if (refund == null) {
            throw new BusinessException("退款记录不存在");
        }
        InterventionMeta meta = RefundViewUtil.extractInterventionMeta(refund.getDescription());
        if (meta == null || !INTERVENTION_PENDING.equals(meta.status)) {
            throw new BusinessException("当前退款未处于平台介入处理中");
        }

        Boolean approved = parseBoolean(body == null ? null : body.get("approved"));
        if (approved == null) {
            throw new BusinessException("请指定平台裁定结果");
        }
        String reason = body == null ? null : trimToNull(body.get("reason"));

        Long merchantId = resolveSingleMerchantId(refund.getOrderId());
        Order order = null;
        if (approved) {
            RefundRequest refundUpdate = new RefundRequest();
            refundUpdate.setStatus(Constants.RefundStatus.REFUNDED);
            refundUpdate.setDescription(RefundViewUtil.upsertInterventionMetadata(
                    refund.getDescription(),
                    INTERVENTION_APPROVED,
                    StringUtils.hasText(reason) ? reason : "平台介入后同意退款",
                    LocalDateTime.now()
            ));
            int refundUpdated = refundMapper.update(refundUpdate,
                    new LambdaUpdateWrapper<RefundRequest>()
                            .eq(RefundRequest::getId, id)
                            .eq(RefundRequest::getStatus, Constants.RefundStatus.REJECTED));
            if (refundUpdated == 0) {
                throw new BusinessException("退款状态已变更，请刷新后重试");
            }
            refund.setStatus(Constants.RefundStatus.REFUNDED);
            refund.setDescription(refundUpdate.getDescription());
            order = performRefundPayout(refund);

            dispatchRefundMessage(
                    RabbitMqNames.ROUTING_REFUND_REFUNDED,
                    refund.getUserId(),
                    "平台已同意退款",
                    "平台介入后已同意您的退款申请，退款金额已退回钱包",
                    refund.getOrderId(),
                    refund.getId(),
                    order != null ? order.getOrderNo() : null,
                    refund.getAmount()
            );
        } else {
            String updatedDescription = RefundViewUtil.upsertInterventionMetadata(
                    refund.getDescription(),
                    INTERVENTION_REJECTED,
                    StringUtils.hasText(reason) ? reason : "平台维持商家拒绝结果",
                    LocalDateTime.now()
            );
            RefundRequest refundUpdate = new RefundRequest();
            refundUpdate.setDescription(updatedDescription);
            int refundUpdated = refundMapper.update(refundUpdate,
                    new LambdaUpdateWrapper<RefundRequest>()
                            .eq(RefundRequest::getId, id)
                            .eq(RefundRequest::getStatus, Constants.RefundStatus.REJECTED));
            if (refundUpdated == 0) {
                throw new BusinessException("退款状态已变更，请刷新后重试");
            }
            refund.setDescription(updatedDescription);
            dispatchRefundMessage(
                    RabbitMqNames.ROUTING_REFUND_REJECTED,
                    refund.getUserId(),
                    "平台维持拒绝结果",
                    "平台已处理您的介入申请，当前维持拒绝退款结果" + (StringUtils.hasText(reason) ? "，原因：" + reason : ""),
                    refund.getOrderId(),
                    refund.getId(),
                    null,
                    refund.getAmount()
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "refund");
        payload.put("refundId", refund.getId());
        payload.put("orderId", refund.getOrderId());
        payload.put("status", approved ? "intervention_approved" : "intervention_rejected");
        managementWorkbenchRealtimeService.notifyAdmins("refund-updated", payload);
        managementWorkbenchRealtimeService.notifyMerchant(merchantId, "refund-updated", payload);

        RefundRequest latest = refundMapper.selectById(id);
        enrichRefundWithContext(latest);
        return Result.success(approved ? "平台已裁定同意退款" : "平台已维持拒绝结果", latest);
    }

    @GetMapping("/statistics/competition")
    public Result<?> competitionStatistics(HttpServletRequest request) {
        moduleSwitchService.requireEnabled("refund");
        if (!moduleSwitchService.isEnabled("refund")) {
            return Result.success(new LinkedHashMap<>());
        }
        String role = (String) request.getAttribute("role");
        if (!Constants.Role.ADMIN.equals(role)) {
            throw new BusinessException("无权查看");
        }

        List<RefundRequest> refunds = refundMapper.selectList(
                new LambdaQueryWrapper<RefundRequest>().orderByDesc(RefundRequest::getCreateTime));
        List<Map<String, Object>> reasons = new ArrayList<>();
        Map<String, Long> reasonCounter = new LinkedHashMap<>();
        long pending = 0L;
        long rejected = 0L;
        long refunded = 0L;
        long interventionPending = 0L;
        long interventionResolved = 0L;

        for (RefundRequest refund : refunds) {
            if (refund == null) {
                continue;
            }
            if (refund.getStatus() != null) {
                if (refund.getStatus() == Constants.RefundStatus.PENDING) {
                    pending++;
                } else if (refund.getStatus() == Constants.RefundStatus.REJECTED) {
                    rejected++;
                } else if (refund.getStatus() == Constants.RefundStatus.REFUNDED) {
                    refunded++;
                }
            }
            if (StringUtils.hasText(refund.getReason())) {
                reasonCounter.merge(refund.getReason().trim(), 1L, Long::sum);
            }
            InterventionMeta meta = RefundViewUtil.extractInterventionMeta(refund.getDescription());
            if (meta != null) {
                if (INTERVENTION_PENDING.equals(meta.status)) {
                    interventionPending++;
                } else {
                    interventionResolved++;
                }
            }
        }

        reasonCounter.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("reason", entry.getKey());
                    item.put("count", entry.getValue());
                    reasons.add(item);
                });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", refunds.size());
        result.put("pending", pending);
        result.put("rejected", rejected);
        result.put("refunded", refunded);
        result.put("interventionPending", interventionPending);
        result.put("interventionResolved", interventionResolved);
        result.put("reasonDistribution", reasons);
        return Result.success(result);
    }

    private void ensureUserRole(String role) {
        if (!Constants.Role.USER.equals(role)) {
            throw new BusinessException("无权操作");
        }
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private void enrichRefundWithContext(RefundRequest refund) {
        if (refund == null) {
            return;
        }
        Order order = orderMapper.selectById(refund.getOrderId());
        if (order != null) {
            refund.setOrderNo(order.getOrderNo());
        }
        User user = userMapper.selectById(refund.getUserId());
        if (user != null) {
            refund.setUsername(user.getUsername());
        }
        RefundViewUtil.enrichRefundView(refund);
    }

    private Order performRefundPayout(RefundRequest refund) {
        Order order = orderMapper.selectById(refund.getOrderId());
        if (order != null) {
            if (order.getStatus() != Constants.OrderStatus.PAID
                    && order.getStatus() != Constants.OrderStatus.SHIPPED
                    && order.getStatus() != Constants.OrderStatus.COMPLETED) {
                throw new BusinessException("当前订单状态不支持退款打款");
            }
            int orderUpdated = orderMapper.casUpdateStatus(
                    order.getId(),
                    order.getStatus(),
                    Constants.OrderStatus.REFUNDED
            );
            if (orderUpdated == 0) {
                throw new BusinessException("订单状态已变更，请刷新后重试");
            }
            order.setStatus(Constants.OrderStatus.REFUNDED);
        }

        userMapper.addBalance(refund.getUserId(), refund.getAmount());

        User afterUser = userMapper.selectById(refund.getUserId());
        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(refund.getUserId());
        tx.setType(Constants.WalletType.REFUND);
        tx.setAmount(refund.getAmount());
        tx.setBalanceBefore(afterUser.getBalance().subtract(refund.getAmount()));
        tx.setBalanceAfter(afterUser.getBalance());
        if (order != null) {
            tx.setOrderNo(order.getOrderNo());
        }
        tx.setDescription("售后退款");
        walletTransactionMapper.insert(tx);
        emitRefundRecommendationEvents(refund, order);
        return order;
    }

    private void checkMerchantOwnsRefund(Long orderId, Long merchantId) {
        java.util.List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        if (items.isEmpty()) {
            throw new BusinessException("订单不存在");
        }
        boolean ownsAll = items.stream().allMatch(item -> {
            Product product = productService.getById(item.getProductId());
            return product != null && java.util.Objects.equals(product.getMerchantId(), merchantId);
        });
        if (!ownsAll) {
            throw new BusinessException(403, "该退款所属订单包含其他商家商品，商家无权访问或操作");
        }
    }

    private Long resolveSingleMerchantId(Long orderId) {
        java.util.List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        if (items.isEmpty()) {
            throw new BusinessException("订单不存在");
        }
        java.util.Set<Long> merchantIds = new java.util.HashSet<>();
        for (OrderItem item : items) {
            Product product = productService.getById(item.getProductId());
            if (product == null || product.getMerchantId() == null) {
                throw new BusinessException("订单商品数据异常，暂不支持退款");
            }
            merchantIds.add(product.getMerchantId());
            if (merchantIds.size() > 1) {
                throw new BusinessException("多商家合并订单暂不支持整单在线退款");
            }
        }
        return merchantIds.iterator().next();
    }

    private void dispatchRefundMessage(String routingKey,
                                       Long userId,
                                       String title,
                                       String content,
                                       Long orderId,
                                       Long refundId,
                                       String orderNo,
                                       java.math.BigDecimal amount) {
        if (mqEventPublisher.isEnabled()) {
            JSONObject payload = buildNotificationPayload(userId, title, content, "order", orderId);
            payload.put("refundId", refundId);
            payload.put("orderId", orderId);
            payload.put("orderNo", orderNo);
            payload.put("amount", amount);
            mqEventPublisher.publishEvent(routingKey, refundId != null ? String.valueOf(refundId) : String.valueOf(orderId), payload);
            return;
        }

        sendMessage(userId, title, content, "order", orderId);
    }

    private void emitRefundRecommendationEvents(RefundRequest refund, Order order) {
        if (refund == null || refund.getOrderId() == null || refund.getUserId() == null) {
            return;
        }
        java.util.List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, refund.getOrderId()));
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }
        for (OrderItem item : orderItems) {
            if (item == null || item.getProductId() == null) {
                continue;
            }
            RecommendationEventDTO eventDTO = new RecommendationEventDTO();
            eventDTO.setEventType(Constants.RecommendationEventType.REFUND);
            eventDTO.setProductId(item.getProductId());
            eventDTO.setOrderId(refund.getOrderId());
            eventDTO.setAmount(refund.getAmount());
            eventDTO.setEventTime(java.time.LocalDateTime.now());
            eventDTO.setTraceId(order == null ? null : order.getOrderNo());
            eventDTO.setScene("refund_approved");
            recommendationAsyncService.recordRecommendationEventAsync(refund.getUserId(), eventDTO);
        }
    }

    private JSONObject buildNotificationPayload(Long userId, String title, String content, String type, Long relatedId) {
        JSONObject payload = new JSONObject();
        payload.put("userId", userId);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("messageType", type);
        payload.put("relatedId", relatedId);
        return payload;
    }

    private void sendMessage(Long userId, String title, String content, String type, Long relatedId) {
        if (!moduleSwitchService.isEnabled("message")) {
            return;
        }
        Message message = new Message();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setType(type);
        message.setRelatedId(relatedId);
        message.setIsRead(0);
        messageMapper.insert(message);
        managementWorkbenchRealtimeService.notifyUserMessageChanged(userId, "user-message-created");
    }

    private <T> IPage<T> emptyPage(int page, int size) {
        Page<T> empty = new Page<>(page, size);
        empty.setRecords(java.util.Collections.emptyList());
        empty.setTotal(0);
        return empty;
    }
}
