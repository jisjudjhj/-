package com.ecommerce.service.impl;

import com.alibaba.fastjson2.JSONObject;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.utils.CouponUtil;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Coupon;
import com.ecommerce.entity.Message;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.RefundRequest;
import com.ecommerce.entity.AnalyticsRecommendationExposure;
import com.ecommerce.entity.SeckillActivityApply;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserCoupon;
import com.ecommerce.entity.WalletTransaction;
import com.ecommerce.entity.Address;
import com.ecommerce.mapper.AddressMapper;
import com.ecommerce.mapper.AnalyticsRecommendationExposureMapper;
import com.ecommerce.mapper.CartItemMapper;
import com.ecommerce.mapper.CouponMapper;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.RefundMapper;
import com.ecommerce.mapper.SeckillActivityApplyMapper;
import com.ecommerce.mapper.UserCouponMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.mapper.WalletTransactionMapper;
import com.ecommerce.mq.MqEventPublisher;
import com.ecommerce.mq.RabbitMqNames;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.OrderAsyncService;
import com.ecommerce.service.RedisStockService;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private AnalyticsRecommendationExposureMapper analyticsRecommendationExposureMapper;

    @Autowired
    private OrderAsyncService orderAsyncService;

    @Autowired
    private MqEventPublisher mqEventPublisher;

    @Autowired
    private OrderMessageHelper orderMessageHelper;

    @Autowired
    private SeckillActivityApplyMapper seckillActivityApplyMapper;

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private ProductService productService;

    @Value("${order.timeout-minutes:30}")
    private int timeoutMinutes;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public List<Order> createOrders(Long userId, String address, String receiverName,
                                    String receiverPhone, String remark, List<OrderItem> items,
                                    Long userCouponId, Map<Long, Long> splitCoupons) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("订单商品不能为空");
        }

        List<OrderItem> normalizedItems = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException("订单商品参数不完整");
            }
            OrderItem normalized = new OrderItem();
            normalized.setProductId(item.getProductId());
            normalized.setQuantity(item.getQuantity());
            normalizedItems.add(normalized);
        }

        Map<Long, List<OrderItem>> merchantGroupedItems = new LinkedHashMap<>();
        for (OrderItem item : normalizedItems) {
            Product product = productMapper.selectById(item.getProductId());
            if (product == null || product.getStatus() != Constants.ProductStatus.ON_SHELF) {
                throw new BusinessException("商品不存在或已下架: " + item.getProductId());
            }
            Long merchantId = product.getMerchantId() == null ? 0L : product.getMerchantId();
            merchantGroupedItems.computeIfAbsent(merchantId, ignored -> new ArrayList<>()).add(item);
        }

        if (merchantGroupedItems.size() <= 1) {
            Long merchantId = merchantGroupedItems.keySet().iterator().next();
            Long splitCouponId = splitCoupons == null ? null : splitCoupons.get(merchantId);
            Long targetCouponId = userCouponId != null ? userCouponId : splitCouponId;
            return Collections.singletonList(
                    createOrder(userId, address, receiverName, receiverPhone, remark, normalizedItems, targetCouponId)
            );
        }

        if (userCouponId != null && (splitCoupons == null || splitCoupons.isEmpty())) {
            throw new BusinessException("跨店结算请使用AI推荐优惠方案后再提交");
        }
        if (userCouponId != null && splitCoupons != null && !splitCoupons.isEmpty()) {
            throw new BusinessException("请勿同时传入单券与拆单券方案");
        }

        Map<Long, Long> merchantCouponMapping = new LinkedHashMap<>();
        if (splitCoupons != null && !splitCoupons.isEmpty()) {
            Set<Long> duplicatedCoupons = new HashSet<>();
            Set<Long> uniqueCoupons = new HashSet<>();
            for (Map.Entry<Long, Long> entry : splitCoupons.entrySet()) {
                Long merchantId = entry.getKey();
                Long couponId = entry.getValue();
                if (merchantId == null || couponId == null) {
                    continue;
                }
                if (!merchantGroupedItems.containsKey(merchantId)) {
                    throw new BusinessException("拆单券方案包含无效商家");
                }
                merchantCouponMapping.put(merchantId, couponId);
                if (!uniqueCoupons.add(couponId)) {
                    duplicatedCoupons.add(couponId);
                }
            }
            if (!duplicatedCoupons.isEmpty()) {
                throw new BusinessException("同一张优惠券不能分配给多个店铺订单");
            }
        }

        List<Order> splitOrders = new ArrayList<>();
        for (Map.Entry<Long, List<OrderItem>> groupedEntry : merchantGroupedItems.entrySet()) {
            Long merchantId = groupedEntry.getKey();
            List<OrderItem> groupedItems = groupedEntry.getValue();
            Long groupedCouponId = merchantCouponMapping.get(merchantId);
            splitOrders.add(createOrder(userId, address, receiverName, receiverPhone, remark, groupedItems, groupedCouponId));
        }
        return splitOrders;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Order createOrder(Long userId, String address, String receiverName,
                             String receiverPhone, String remark, List<OrderItem> items,
                             Long userCouponId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("订单商品不能为空");
        }

        if (userCouponId != null) {
            moduleSwitchService.requireEnabled("coupon");
        }

        String orderNo = "ORD" + IdUtil.getSnowflakeNextIdStr();

        long recentDupCount = this.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, Constants.OrderStatus.PENDING)
                .gt(Order::getCreateTime, LocalDateTime.now().minusMinutes(Constants.OrderRateLimit.DUPLICATE_CHECK_WINDOW_MINUTES)));
        if (recentDupCount >= Constants.OrderRateLimit.MAX_PENDING_ORDERS_PER_WINDOW) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        List<Product> validatedProducts = new ArrayList<>(items.size());
        Long merchantId = null;
        for (OrderItem item : items) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException("商品数量必须大于0");
            }
            Product product = productMapper.selectById(item.getProductId());
            if (product == null || product.getStatus() != Constants.ProductStatus.ON_SHELF) {
                throw new BusinessException("商品不存在或已下架: " + item.getProductId());
            }
            if (moduleSwitchService.isEnabled("seckill")
                    && seckillService.isActiveSeckillProduct(product.getId())) {
                throw new BusinessException("秒杀商品请直接使用秒杀购买");
            }
            if (merchantId == null) {
                merchantId = product.getMerchantId();
            } else if (!Objects.equals(merchantId, product.getMerchantId())) {
                throw new BusinessException("暂不支持跨商家合并下单，请按商家分别提交订单");
            }
            validatedProducts.add(product);
        }

        List<Long> deductedProductIds = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            Product product = validatedProducts.get(i);
            boolean deducted = redisStockService.deductStock(product.getId(), item.getQuantity());
            if (!deducted) {
                rollbackStockDeductions(deductedProductIds, items);
                throw new BusinessException("商品库存不足: " + product.getName());
            }
            deductedProductIds.add(product.getId());

            item.setProductName(product.getName());
            item.setProductImage(product.getImage());
            item.setPrice(product.getPrice());
            item.setSubtotal(product.getPrice().multiply(new BigDecimal(item.getQuantity())));
            totalAmount = totalAmount.add(item.getSubtotal());
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        Long lockedUserCouponId = null;
        if (userCouponId != null) {
            UserCoupon uc = userCouponMapper.selectById(userCouponId);
            if (uc == null || !Objects.equals(uc.getUserId(), userId)) {
                throw new BusinessException("优惠券不存在");
            }
            if (uc.getStatus() != 0 || uc.getOrderId() != null) {
                throw new BusinessException("优惠券已被使用或正在处理中");
            }

            com.ecommerce.entity.Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon == null || coupon.getEndTime() == null || !LocalDateTime.now().isBefore(coupon.getEndTime())) {
                throw new BusinessException("优惠券已过期");
            }
            Integer scopeType = coupon.getScopeType() == null ? Constants.CouponScope.PLATFORM : coupon.getScopeType();
            if (scopeType == Constants.CouponScope.MERCHANT_STORE
                    && !Objects.equals(coupon.getMerchantId(), merchantId)) {
                throw new BusinessException("该优惠券仅可用于指定店铺订单");
            }
            if (coupon.getMinAmount() != null && totalAmount.compareTo(coupon.getMinAmount()) < 0) {
                throw new BusinessException("未达到优惠券使用门槛");
            }

            discountAmount = CouponUtil.calculateDiscount(coupon, totalAmount);
            UserCoupon updateCoupon = new UserCoupon();
            updateCoupon.setStatus(1);
            updateCoupon.setUseTime(LocalDateTime.now());
            int lockedRows = userCouponMapper.update(updateCoupon,
                    new LambdaUpdateWrapper<UserCoupon>()
                            .eq(UserCoupon::getId, userCouponId)
                            .eq(UserCoupon::getUserId, userId)
                            .eq(UserCoupon::getStatus, 0)
                            .isNull(UserCoupon::getOrderId));
            if (lockedRows == 0) {
                throw new BusinessException("优惠券已被其他订单占用");
            }
            lockedUserCouponId = userCouponId;
        }

        BigDecimal finalAmount = totalAmount.subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setTotalAmount(finalAmount);
        order.setOriginalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setStatus(Constants.OrderStatus.PENDING);
        order.setAddress(address);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setRemark(remark);
        order.setUserCouponId(lockedUserCouponId);
        this.save(order);

        if (lockedUserCouponId != null) {
            UserCoupon bindCoupon = new UserCoupon();
            bindCoupon.setOrderId(order.getId());
            int bindRows = userCouponMapper.update(bindCoupon,
                    new LambdaUpdateWrapper<UserCoupon>()
                            .eq(UserCoupon::getId, lockedUserCouponId)
                            .eq(UserCoupon::getUserId, userId)
                            .eq(UserCoupon::getStatus, 1)
                            .isNull(UserCoupon::getOrderId));
            if (bindRows == 0) {
                throw new BusinessException("优惠券绑定失败，请重试");
            }
        }

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        Set<Long> orderedProductIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toSet());
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getProductId, orderedProductIds));

        order.setItems(items);
        productService.evictProductCaches(orderedProductIds);
        orderMessageHelper.notifyOrderCreated(order, finalAmount, discountAmount);

        log.info("[订单创建] 订单号={}, 用户={}, 原价={}, 优惠={}, 实付={}",
                orderNo, userId, totalAmount, discountAmount, finalAmount);
        return order;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Order createSeckillOrder(Long userId, Long seckillApplyId, Integer quantity,
                                    String address, String receiverName, String receiverPhone, String remark) {
        if (!StringUtils.hasText(address)) {
            throw new BusinessException("收货地址不能为空");
        }
        if (!StringUtils.hasText(receiverName)) {
            throw new BusinessException("收货人不能为空");
        }
        if (!StringUtils.hasText(receiverPhone)) {
            throw new BusinessException("收货电话不能为空");
        }

        SeckillActivityApply apply = seckillService.requireOrderableApply(seckillApplyId, userId, quantity);
        Product product = apply.getProduct();

        int lockRows = seckillActivityApplyMapper.lockSoldCount(seckillApplyId, quantity);
        if (lockRows == 0) {
            throw new BusinessException("秒杀库存不足，请重试");
        }

        boolean deducted = redisStockService.deductStock(product.getId(), quantity);
        if (!deducted) {
            throw new BusinessException("商品库存不足");
        }

        String orderNo = "ORD" + IdUtil.getSnowflakeNextIdStr();
        BigDecimal originalAmount = product.getPrice().multiply(new BigDecimal(quantity));
        BigDecimal totalAmount = apply.getSeckillPrice().multiply(new BigDecimal(quantity));
        BigDecimal discountAmount = originalAmount.subtract(totalAmount);
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            discountAmount = BigDecimal.ZERO;
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setTotalAmount(totalAmount);
        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(discountAmount);
        order.setUserCouponId(null);
        order.setSeckillActivityId(apply.getActivityId());
        order.setSeckillApplyId(apply.getId());
        order.setStatus(Constants.OrderStatus.PENDING);
        order.setAddress(address.trim());
        order.setReceiverName(receiverName.trim());
        order.setReceiverPhone(receiverPhone.trim());
        order.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        this.save(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductImage(product.getImage());
        item.setPrice(apply.getSeckillPrice());
        item.setQuantity(quantity);
        item.setSubtotal(totalAmount);
        orderItemMapper.insert(item);

        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, product.getId()));

        order.setItems(Collections.singletonList(item));
        order.setSeckillOrder(true);
        productService.evictProductCaches(Collections.singletonList(product.getId()));
        orderMessageHelper.notifyOrderCreated(order, totalAmount, discountAmount);
        log.info("[秒杀下单] 订单号={}, 用户={}, 报名={}, 数量={}, 实付={}",
                orderNo, userId, seckillApplyId, quantity, totalAmount);
        return order;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void payOrder(Long orderId, Long userId) {
        moduleSwitchService.requireEnabled("wallet");
        Order order = this.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != Constants.OrderStatus.PENDING) {
            throw new BusinessException("订单状态不允许支付，可能已被处理");
        }

        long txCount = walletTransactionMapper.selectCount(
                new LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getOrderNo, order.getOrderNo())
                        .eq(WalletTransaction::getType, Constants.WalletType.PAY));
        if (txCount > 0) {
            throw new BusinessException("订单已支付，请勿重复操作");
        }

        int statusUpdated = baseMapper.casUpdateStatus(orderId,
                Constants.OrderStatus.PENDING, Constants.OrderStatus.PAID);
        if (statusUpdated == 0) {
            throw new BusinessException("订单状态已变更，支付失败");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        BigDecimal totalAmount = order.getTotalAmount();

        int balanceUpdated = userMapper.deductBalance(userId, totalAmount);
        if (balanceUpdated == 0) {
            baseMapper.casUpdateStatus(orderId, Constants.OrderStatus.PAID, Constants.OrderStatus.PENDING);
            throw new BusinessException("余额不足，需支付 ¥" + totalAmount.toPlainString() +
                    "，当前余额 ¥" + user.getBalance().toPlainString());
        }

        Order paidOrder = this.getById(orderId);
        if (paidOrder != null) {
            paidOrder.setPayTime(LocalDateTime.now());
            this.updateById(paidOrder);
        }

        User afterUser = userMapper.selectById(userId);
        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(userId);
        tx.setType(Constants.WalletType.PAY);
        tx.setAmount(totalAmount.negate());
        tx.setBalanceBefore(afterUser.getBalance().add(totalAmount));
        tx.setBalanceAfter(afterUser.getBalance());
        tx.setOrderNo(order.getOrderNo());
        tx.setDescription("支付订单 " + order.getOrderNo());
        walletTransactionMapper.insert(tx);

        Order eventOrder = paidOrder != null ? paidOrder : order;
        eventOrder.setStatus(Constants.OrderStatus.PAID);
        orderMessageHelper.notifyOrderPaid(eventOrder, totalAmount);
        recordPurchaseBehaviorAsync(eventOrder.getId(), userId);

        log.info("[订单支付] 订单号={}, 用户={}, 金额={}", order.getOrderNo(), userId, totalAmount);
    }

    @Override
    public IPage<Order> getUserOrders(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        if (status != null && status >= 0) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        IPage<Order> orderPage = this.page(new Page<>(page, size), wrapper);
        batchFillOrderItems(orderPage.getRecords());
        return orderPage;
    }

    @Override
    public Map<String, Object> getUserOrdersByCursor(Long userId, Integer status, Long cursorId, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        if (status != null && status >= 0) {
            wrapper.eq(Order::getStatus, status);
        }
        if (cursorId != null && cursorId > 0L) {
            wrapper.lt(Order::getId, cursorId);
        }
        wrapper.orderByDesc(Order::getId)
                .last("LIMIT " + (safeSize + 1));

        List<Order> records = this.list(wrapper);
        boolean hasMore = records.size() > safeSize;
        if (hasMore) {
            records = new ArrayList<>(records.subList(0, safeSize));
        }

        batchFillOrderItems(records);
        Long nextCursor = hasMore && !records.isEmpty() ? records.get(records.size() - 1).getId() : null;

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("size", safeSize);
        result.put("hasMore", hasMore);
        result.put("nextCursor", nextCursor);
        return result;
    }

    @Override
    public IPage<Order> getAllOrders(Integer status, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (status != null && status >= 0) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        IPage<Order> orderPage = this.page(new Page<>(page, size), wrapper);
        batchFillOrderItems(orderPage.getRecords());
        batchFillOrderUsernames(orderPage.getRecords());
        return orderPage;
    }

    @Override
    public IPage<Order> getMerchantOrders(Long merchantId, Integer status, int page, int size) {
        IPage<Order> orderPage = baseMapper.selectMerchantOrderPage(new Page<>(page, size), merchantId, status);
        batchFillOrderItems(orderPage.getRecords());
        batchFillOrderUsernames(orderPage.getRecords());
        return orderPage;
    }

    @Override
    public Order getOrderDetail(Long orderId) {
        Order order = this.getById(orderId);
        if (order != null) {
            fillOrderItems(order);
        }
        return order;
    }

    private static final Map<Integer, Set<Integer>> VALID_TRANSITIONS;

    static {
        Map<Integer, Set<Integer>> transitions = new HashMap<>();
        transitions.put(Constants.OrderStatus.PENDING,
                new HashSet<>(Arrays.asList(Constants.OrderStatus.PAID, Constants.OrderStatus.CANCELLED)));
        transitions.put(Constants.OrderStatus.PAID,
                new HashSet<>(Arrays.asList(Constants.OrderStatus.SHIPPED, Constants.OrderStatus.CANCELLED)));
        transitions.put(Constants.OrderStatus.SHIPPED,
                new HashSet<>(Collections.singletonList(Constants.OrderStatus.COMPLETED)));
        transitions.put(Constants.OrderStatus.COMPLETED, Collections.emptySet());
        transitions.put(Constants.OrderStatus.CANCELLED, Collections.emptySet());
        transitions.put(Constants.OrderStatus.REFUNDED, Collections.emptySet());
        VALID_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    @Override
    public boolean updateOrderStatus(Long orderId, Integer newStatus) {
        Order order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (Objects.equals(newStatus, Constants.OrderStatus.CANCELLED)) {
            throw new BusinessException("请使用专用取消流程，确保退款、库存和优惠券回滚一致。");
        }
        int oldStatus = order.getStatus();
        Set<Integer> allowed = VALID_TRANSITIONS.getOrDefault(oldStatus, Collections.emptySet());
        if (!allowed.contains(newStatus)) {
            throw new BusinessException("订单状态不允许从 " + oldStatus + " 变为 " + newStatus);
        }
        int updated = baseMapper.casUpdateStatus(orderId, oldStatus, newStatus);
        if (updated == 0) {
            throw new BusinessException("订单状态更新失败，可能已被其他操作修改");
        }

        String[] statusNames = {"待支付", "已支付", "已发货", "已完成", "已取消", "已退款"};
        if (newStatus >= 0 && newStatus < statusNames.length) {
            order.setStatus(newStatus);
            orderMessageHelper.notifyOrderStatusChanged(order, statusNames[newStatus]);
        }
        return true;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId, Long userId) {
        Order order = this.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }

        boolean wasPaid = order.getStatus() == Constants.OrderStatus.PAID;
        int oldStatus = order.getStatus();
        if (oldStatus != Constants.OrderStatus.PENDING && oldStatus != Constants.OrderStatus.PAID) {
            throw new BusinessException("当前订单状态不允许取消");
        }

        long pendingRefundCount = refundMapper.selectCount(
                new LambdaQueryWrapper<RefundRequest>()
                        .eq(RefundRequest::getOrderId, orderId)
                        .in(RefundRequest::getStatus,
                                Constants.RefundStatus.PENDING,
                                Constants.RefundStatus.APPROVED));
        if (pendingRefundCount > 0) {
            throw new BusinessException("订单退款申请处理中，请勿重复取消");
        }

        int statusUpdated = baseMapper.casUpdateStatus(orderId, oldStatus, Constants.OrderStatus.CANCELLED);
        if (statusUpdated == 0) {
            throw new BusinessException("取消失败，订单状态已变更");
        }

        List<OrderItem> orderItems = loadOrderItems(orderId);
        restoreOrderStock(orderItems);
        restoreSeckillStockIfNeeded(order);
        evictOrderProductCaches(orderItems);
        releaseCouponIfNeeded(order);

        if (wasPaid) {
            userMapper.addBalance(userId, order.getTotalAmount());

            User afterUser = userMapper.selectById(userId);
            WalletTransaction tx = new WalletTransaction();
            tx.setUserId(userId);
            tx.setType(Constants.WalletType.REFUND);
            tx.setAmount(order.getTotalAmount());
            tx.setBalanceBefore(afterUser.getBalance().subtract(order.getTotalAmount()));
            tx.setBalanceAfter(afterUser.getBalance());
            tx.setOrderNo(order.getOrderNo());
            tx.setDescription("取消退款 " + order.getOrderNo());
            walletTransactionMapper.insert(tx);
        }

        order.setStatus(Constants.OrderStatus.CANCELLED);
        orderMessageHelper.notifyOrderCancelled(order, wasPaid);

        log.info("[订单取消] 订单号={}, 用户={}, 已支付={}", order.getOrderNo(), userId, wasPaid);
        return true;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public boolean cancelTimeoutOrder(Long orderId) {
        Order order = this.getById(orderId);
        if (order == null || order.getStatus() != Constants.OrderStatus.PENDING) {
            return false;
        }

        int updated = baseMapper.casUpdateStatus(orderId,
                Constants.OrderStatus.PENDING, Constants.OrderStatus.CANCELLED);
        if (updated == 0) {
            return false;
        }

        List<OrderItem> orderItems = loadOrderItems(orderId);
        restoreOrderStock(orderItems);
        restoreSeckillStockIfNeeded(order);
        evictOrderProductCaches(orderItems);
        releaseCouponIfNeeded(order);
        order.setStatus(Constants.OrderStatus.CANCELLED);
        orderMessageHelper.notifyOrderTimeout(order);

        log.info("[订单超时] 订单 {} 已自动取消", order.getOrderNo());
        return true;
    }

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userMapper.selectCount(null));
        stats.put("totalProducts", productMapper.selectCount(null));
        stats.put("totalOrders", this.count());
        stats.put("totalRevenue", baseMapper.selectTotalRevenue());
        stats.put("recentStats", baseMapper.selectRecentStats());
        return stats;
    }

    private void fillOrderItems(Order order) {
        order.setItems(loadOrderItems(order.getId()));
    }

    private void batchFillOrderItems(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<Long> orderIds = orders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return;
        }

        List<OrderItem> allItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getOrderId, orderIds)
                .orderByAsc(OrderItem::getId));
        Map<Long, List<OrderItem>> itemsByOrderId = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        for (Order order : orders) {
            List<OrderItem> items = itemsByOrderId.get(order.getId());
            order.setItems(items == null ? Collections.emptyList() : items);
        }
    }

    private void batchFillOrderUsernames(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<Long> userIds = orders.stream()
                .map(Order::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }

        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));

        for (Order order : orders) {
            User user = userMap.get(order.getUserId());
            if (user != null) {
                order.setUsername(user.getUsername());
            }
        }
    }

    private Set<Long> filterExclusiveMerchantOrderIds(Set<Long> merchantProductIds, Set<Long> candidateOrderIds) {
        if (merchantProductIds.isEmpty() || candidateOrderIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<OrderItem> allOrderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, candidateOrderIds));
        return allOrderItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId))
                .entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isEmpty()
                        && entry.getValue().stream().allMatch(item -> merchantProductIds.contains(item.getProductId())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private List<OrderItem> loadOrderItems(Long orderId) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
    }

    private void restoreOrderStock(List<OrderItem> items) {
        for (OrderItem item : items) {
            redisStockService.restoreStock(item.getProductId(), item.getQuantity());
        }
    }

    private void evictOrderProductCaches(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        productService.evictProductCaches(items.stream()
                .map(OrderItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    }

    private void restoreSeckillStockIfNeeded(Order order) {
        if (order == null || order.getSeckillApplyId() == null) {
            return;
        }
        List<OrderItem> items = loadOrderItems(order.getId());
        int totalQty = items.stream()
                .map(OrderItem::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (totalQty <= 0) {
            return;
        }
        seckillActivityApplyMapper.restoreSoldCount(order.getSeckillApplyId(), totalQty);
        log.info("[秒杀库存回补] orderId={}, applyId={}, quantity={}",
                order.getId(), order.getSeckillApplyId(), totalQty);
    }

    private void releaseCouponIfNeeded(Order order) {
        if (order.getUserCouponId() == null) {
            return;
        }

        UserCoupon coupon = userCouponMapper.selectById(order.getUserCouponId());
        if (coupon != null && coupon.getStatus() == 1) {
            coupon.setStatus(0);
            coupon.setUseTime(null);
            coupon.setOrderId(null);
            userCouponMapper.updateById(coupon);
            log.info("[订单] 释放优惠券 userCouponId={}", order.getUserCouponId());
        }
    }

    private void recordPurchaseBehaviorAsync(Long orderId, Long userId) {
        orderAsyncService.recordPurchaseBehavior(orderId, userId);
    }

    private void rollbackStockDeductions(List<Long> productIds, List<OrderItem> items) {
        for (int i = 0; i < productIds.size(); i++) {
            try {
                redisStockService.compensateRedisStock(productIds.get(i), items.get(i).getQuantity());
            } catch (Exception e) {
                log.error("[订单创建] 库存回滚失败 productId={}: {}", productIds.get(i), e.getMessage());
            }
        }
    }

    @Override
    public Map<String, String> resolveAddress(Long addressId, Long userId) {
        Address addr = addressMapper.selectById(addressId);
        if (addr == null || !addr.getUserId().equals(userId)) {
            throw new BusinessException("收货地址不存在");
        }
        String province = addr.getProvince() != null ? addr.getProvince() : "";
        String city = addr.getCity() != null ? addr.getCity() : "";
        String district = addr.getDistrict() != null ? addr.getDistrict() : "";
        String detail = addr.getDetail() != null ? addr.getDetail() : "";
        String fullAddress = province + (province.equals(city) ? "" : city) + district + detail;

        Map<String, String> result = new HashMap<>();
        result.put("address", fullAddress);
        result.put("receiverName", addr.getReceiverName());
        result.put("receiverPhone", addr.getReceiverPhone());
        return result;
    }

    @Override
    public void checkMerchantOwnsOrder(Long orderId, Long merchantId) {
        List<OrderItem> items = loadOrderItems(orderId);
        if (items.isEmpty()) {
            throw new BusinessException("订单不存在");
        }
        Set<Long> productIds = items.stream()
                .map(OrderItem::getProductId).collect(Collectors.toSet());
        List<Product> products = productMapper.selectBatchIds(new ArrayList<>(productIds));
        boolean ownsAll = products.size() == productIds.size()
                && products.stream().allMatch(p -> Objects.equals(p.getMerchantId(), merchantId));
        if (!ownsAll) {
            throw new BusinessException(403, "该订单包含其他商家商品，商家无权访问或操作");
        }
    }
}
