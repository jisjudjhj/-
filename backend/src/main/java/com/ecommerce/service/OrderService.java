package com.ecommerce.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface OrderService extends IService<Order> {

    List<Order> createOrders(Long userId, String address, String receiverName, String receiverPhone,
                             String remark, List<OrderItem> items, Long userCouponId,
                             Map<Long, Long> splitCoupons);

    void payOrder(Long orderId, Long userId);

    IPage<Order> getUserOrders(Long userId, Integer status, int page, int size);

    Map<String, Object> getUserOrdersByCursor(Long userId, Integer status, Long cursorId, int size);

    IPage<Order> getAllOrders(Integer status, int page, int size);

    IPage<Order> getMerchantOrders(Long merchantId, Integer status, int page, int size);

    Order getOrderDetail(Long orderId);

    boolean updateOrderStatus(Long orderId, Integer status);

    boolean cancelOrder(Long orderId, Long userId);

    boolean cancelTimeoutOrder(Long orderId);

    Map<String, Object> getDashboardStats();

    /**
     * 根据地址ID解析完整收货信息
     */
    Map<String, String> resolveAddress(Long addressId, Long userId);

    /**
     * 校验商家是否拥有该订单中的所有商品
     */
    void checkMerchantOwnsOrder(Long orderId, Long merchantId);

    Order createOrder(Long userId, String address, String receiverName, String receiverPhone, String remark, List<OrderItem> items, Long userCouponId);

    Order createSeckillOrder(Long userId, Long seckillApplyId, Integer quantity,
                             String address, String receiverName, String receiverPhone, String remark);
}
