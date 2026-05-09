package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.RateLimit;
import com.ecommerce.common.Result;
import com.ecommerce.dto.OrderCreateDTO;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Order", description = "下单、支付、取消、确认收货等订单接口")
@SecurityRequirement(name = "BearerAuth")
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private OrderService orderService;

    @PostMapping
    @RateLimit(key = "order:create", window = 30, max = 6, type = RateLimit.LimitType.USER, message = "下单操作过于频繁，请稍后再试")
    @Operation(summary = "创建订单", description = "根据购物项、地址和优惠券信息创建订单。")
    public Result<?> create(@Validated @RequestBody OrderCreateDTO dto, HttpServletRequest request) {
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

        if (address == null || address.isEmpty()) {
            throw new BusinessException("收货地址不能为空");
        }
        if (receiverName == null || receiverName.isEmpty()) {
            throw new BusinessException("收货人不能为空");
        }
        if (receiverPhone == null || receiverPhone.isEmpty()) {
            throw new BusinessException("收货人电话不能为空");
        }

        List<OrderItem> items = new ArrayList<>();
        for (OrderCreateDTO.OrderItemDTO itemDTO : dto.getItems()) {
            OrderItem item = new OrderItem();
            item.setProductId(itemDTO.getProductId());
            item.setQuantity(itemDTO.getQuantity());
            items.add(item);
        }
        List<Order> orders = orderService.createOrders(userId, address,
                receiverName, receiverPhone, dto.getRemark(), items, dto.getUserCouponId(), dto.getSplitCoupons());
        if (orders == null || orders.isEmpty()) {
            throw new BusinessException("订单创建失败，请重试");
        }
        if (orders.size() == 1) {
            return Result.success("下单成功", orders.get(0));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("split", true);
        payload.put("orderCount", orders.size());
        payload.put("primaryOrderId", orders.get(0).getId());
        payload.put("orders", orders);
        return Result.success("下单成功，系统已按店铺拆分订单", payload);
    }

    @GetMapping
    @Operation(summary = "分页查询我的订单", description = "按状态分页查询当前登录用户的订单列表。")
    public Result<?> list(
            @RequestParam(defaultValue = "-1") Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        int safePage = normalizePage(page);
        int safeSize = normalizePageSize(size);
        IPage<Order> result = orderService.getUserOrders(userId, status == -1 ? null : status, safePage, safeSize);
        return Result.success(result);
    }

    @GetMapping("/cursor")
    @Operation(summary = "游标查询我的订单", description = "适用于深分页场景，避免 offset 带来的性能损耗。")
    public Result<?> listByCursor(
            @RequestParam(defaultValue = "-1") Integer status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        int safeSize = normalizePageSize(size);
        return Result.success(orderService.getUserOrdersByCursor(userId, status == -1 ? null : status, cursor, safeSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取订单详情", description = "返回订单详情，支持用户本人、管理员和拥有该订单的商家查看。")
    public Result<?> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        Order order = orderService.getOrderDetail(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        boolean isOwner = order.getUserId().equals(userId);
        boolean isAdmin = Constants.Role.ADMIN.equals(role);
        boolean isMerchant = Constants.Role.MERCHANT.equals(role);
        if (isMerchant) {
            orderService.checkMerchantOwnsOrder(id, userId);
        }
        if (!isOwner && !isAdmin && !isMerchant) {
            return Result.error(403, "无权查看此订单");
        }
        return Result.success(order);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "管理员修改订单状态", description = "管理员用于修改订单状态。")
    public Result<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> params,
                                   HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!Constants.Role.ADMIN.equals(role)) {
            return Result.error(403, "无权操作");
        }
        Integer status = params.get("status");
        if (status == null) {
            throw new BusinessException("请指定订单状态");
        }
        orderService.updateOrderStatus(id, status);
        return Result.success("状态更新成功");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单", description = "当前登录用户取消自己的订单。")
    public Result<?> cancel(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        orderService.cancelOrder(id, userId);
        return Result.success("订单取消成功");
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "支付订单", description = "当前登录用户支付指定订单。")
    public Result<?> pay(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        orderService.payOrder(id, userId);
        return Result.success("支付成功");
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "确认收货", description = "当前登录用户确认已收货，将订单状态更新为已完成。")
    public Result<?> confirm(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Order order = orderService.getById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            return Result.error("订单不存在");
        }
        if (order.getStatus() != Constants.OrderStatus.SHIPPED) {
            return Result.error("订单状态不允许确认收货");
        }
        orderService.updateOrderStatus(id, Constants.OrderStatus.COMPLETED);
        return Result.success("确认收货成功");
    }

    private int normalizePage(int page) {
        return page <= 0 ? 1 : page;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

}
