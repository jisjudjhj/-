package com.ecommerce.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.RateLimit;
import com.ecommerce.common.Result;
import com.ecommerce.dto.SeckillCheckoutPreviewDTO;
import com.ecommerce.dto.SeckillOrderCreateDTO;
import com.ecommerce.entity.Order;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.SeckillGuardService;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private SeckillGuardService seckillGuardService;

    @GetMapping("/products")
    public Result<?> products(@RequestParam(defaultValue = "20") Integer limit,
                              @RequestParam(defaultValue = "false") Boolean includeHistory,
                              @RequestParam(defaultValue = "false") Boolean groupByActivity) {
        if (!moduleSwitchService.isEnabled("seckill")) {
            if (Boolean.TRUE.equals(groupByActivity)) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("items", Collections.emptyList());
                payload.put("activityGroups", Collections.emptyList());
                payload.put("includeHistory", Boolean.TRUE.equals(includeHistory));
                payload.put("groupByActivity", true);
                payload.put("moduleEnabled", false);
                payload.put("emptyReason", "秒杀功能已关闭，请在管理端模块开关中启用后再查看");
                return Result.success(payload);
            }
            return Result.success(Collections.emptyList());
        }
        boolean includeHistoryValue = Boolean.TRUE.equals(includeHistory);
        List<Map<String, Object>> cards = seckillService.getDisplaySeckillProductCards(limit, includeHistoryValue);
        if (Boolean.TRUE.equals(groupByActivity)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("items", cards);
            payload.put("activityGroups", seckillService.getDisplaySeckillActivityGroups(limit, includeHistoryValue));
            payload.put("includeHistory", includeHistoryValue);
            payload.put("groupByActivity", true);
            return Result.success(payload);
        }
        return Result.success(cards);
    }

    @GetMapping("/checkout-preview")
    public Result<?> checkoutPreviewByQuery(@RequestParam(required = false) Long seckillApplyId,
                                            @RequestParam(required = false) Long applyId,
                                            @RequestParam(required = false) Long productId,
                                            @RequestParam(defaultValue = "1") Integer quantity,
                                            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("seckill");
        Long userId = (Long) request.getAttribute("userId");
        Long resolvedApplyId = seckillApplyId != null ? seckillApplyId : applyId;
        if (resolvedApplyId == null && productId != null) {
            resolvedApplyId = seckillService.resolveActiveApplyIdByProductId(productId);
        }
        if (resolvedApplyId == null) {
            throw new BusinessException("秒杀报名不存在");
        }
        return Result.success(seckillService.buildCheckoutPreview(userId, resolvedApplyId, quantity));
    }

    @PostMapping("/checkout-preview")
    public Result<?> checkoutPreview(@Validated @RequestBody SeckillCheckoutPreviewDTO dto,
                                     HttpServletRequest request) {
        moduleSwitchService.requireEnabled("seckill");
        Long userId = (Long) request.getAttribute("userId");
        Long resolvedApplyId = dto.resolveApplyId();
        if (resolvedApplyId == null && dto.getProductId() != null) {
            resolvedApplyId = seckillService.resolveActiveApplyIdByProductId(dto.getProductId());
        }
        if (resolvedApplyId == null) {
            throw new BusinessException("秒杀报名不存在");
        }
        Integer quantity = dto.getQuantity() == null ? 1 : dto.getQuantity();
        return Result.success(seckillService.buildCheckoutPreview(userId, resolvedApplyId, quantity));
    }

    @PostMapping("/orders")
    @RateLimit(key = "seckill:order:create", window = 10, max = 3, type = RateLimit.LimitType.USER, message = "秒杀下单过于频繁，请稍后再试")
    public Result<?> createOrder(@Validated @RequestBody SeckillOrderCreateDTO dto, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("seckill");
        Long userId = (Long) request.getAttribute("userId");

        String address = dto.getAddress();
        String receiverName = dto.getReceiverName();
        String receiverPhone = dto.getReceiverPhone();

        if (dto.getAddressId() != null) {
            Map<String, String> resolved = orderService.resolveAddress(dto.getAddressId(), userId);
            address = resolved.get("address");
            receiverName = resolved.get("receiverName");
            receiverPhone = resolved.get("receiverPhone");
        }

        Long resolvedApplyId = dto.resolveApplyId();
        if (resolvedApplyId == null && dto.getProductId() != null) {
            resolvedApplyId = seckillService.resolveActiveApplyIdByProductId(dto.getProductId());
        }
        if (resolvedApplyId == null) {
            throw new BusinessException("秒杀报名不存在");
        }

        String requestIdempotencyKey = dto.resolveIdempotencyKey(request.getHeader("Idempotency-Key"));
        String effectiveIdempotencyKey = seckillGuardService.resolveIdempotencyKey(
                userId, resolvedApplyId, dto.getQuantity(), requestIdempotencyKey);

        Order completedOrder = loadCompletedIdempotentOrder(userId, effectiveIdempotencyKey);
        if (completedOrder != null) {
            return Result.success("请求已处理，返回已创建订单", completedOrder);
        }

        if (!seckillGuardService.tryAcquireIdempotencyLock(userId, effectiveIdempotencyKey)) {
            completedOrder = loadCompletedIdempotentOrder(userId, effectiveIdempotencyKey);
            if (completedOrder != null) {
                return Result.success("请求已处理，返回已创建订单", completedOrder);
            }
            throw new BusinessException("请求处理中，请勿重复提交");
        }

        boolean hotspotPermitAcquired = false;
        try {
            seckillGuardService.acquireHotspotPermit(resolvedApplyId);
            hotspotPermitAcquired = true;

            Order order = orderService.createSeckillOrder(
                    userId,
                    resolvedApplyId,
                    dto.getQuantity(),
                    address,
                    receiverName,
                    receiverPhone,
                    dto.getRemark()
            );
            seckillGuardService.markIdempotencySuccess(userId, effectiveIdempotencyKey, order.getId());
            return Result.success("秒杀下单成功", order);
        } catch (RuntimeException ex) {
            seckillGuardService.clearIdempotencyLock(userId, effectiveIdempotencyKey);
            throw ex;
        } finally {
            if (hotspotPermitAcquired) {
                seckillGuardService.releaseHotspotPermit(resolvedApplyId);
            }
        }
    }

    private Order loadCompletedIdempotentOrder(Long userId, String idempotencyKey) {
        Long orderId = seckillGuardService.getCompletedOrderId(userId, idempotencyKey);
        if (orderId == null) {
            return null;
        }
        Order order = orderService.getOrderDetail(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            return null;
        }
        return order;
    }
}
