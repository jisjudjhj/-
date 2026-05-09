package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Result;
import com.ecommerce.entity.*;
import com.ecommerce.mapper.CartItemMapper;
import com.ecommerce.mapper.CouponMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserCouponMapper;
import com.ecommerce.service.AiService;
import com.ecommerce.service.CouponAudienceService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.SeckillService;
import com.ecommerce.utils.CouponUtil;
import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private static final String SMART_GUIDE_CACHE_PREFIX = "cart:smart-guide:v1:";
    private static final int SMART_GUIDE_CACHE_TTL_MIN_SECONDS = 30;
    private static final int SMART_GUIDE_CACHE_TTL_MAX_SECONDS = 120;
    private static final int AUTO_CLAIM_MAX_COUNT = 3;
    private static final int CART_DISTINCT_ITEM_LIMIT = 99;
    private static final int CART_ITEM_QUANTITY_LIMIT = 10;
    private static final int CART_TOTAL_QUANTITY_LIMIT = 99;
    private static final String CART_DISTINCT_ITEM_LIMIT_MESSAGE = "购物车最多支持 99 种商品，请先删除部分商品后再试";
    private static final String CART_TOTAL_QUANTITY_LIMIT_MESSAGE = "购物车商品总数量不能超过 99 件，请先删除部分商品后再试";

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private CouponAudienceService couponAudienceService;

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired(required = false)
    private AiService aiService;

    @Autowired(required = false)
    private RedisUtil redisUtil;

    @GetMapping
    public Result<?> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<CartItem> items = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .orderByDesc(CartItem::getUpdateTime));

        items.forEach(item -> {
            Product p = productMapper.selectById(item.getProductId());
            if (p != null) {
                item.setProductName(p.getName());
                item.setProductImage(p.getImage());
                item.setPrice(p.getPrice());
                item.setStock(p.getStock());
                item.setProductStatus(p.getStatus());
                item.setMerchantId(p.getMerchantId());
            }
            normalizeCartItemQuantity(item, p);
            if (isCartItemBlocked(item.getProductId())) {
                item.setCheckoutBlocked(true);
                item.setBlockedReason(SeckillService.CART_BLOCK_REASON);
                item.setSelected(0);
            } else {
                item.setCheckoutBlocked(false);
                item.setBlockedReason(null);
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("totalCount", items.stream().mapToInt(CartItem::getQuantity).sum());
        result.put("selectedCount", items.stream()
                .filter(i -> (i.getCheckoutBlocked() == null || !i.getCheckoutBlocked())
                        && i.getSelected() != null && i.getSelected() == 1)
                .mapToInt(CartItem::getQuantity).sum());
        appendCartDistinctMeta(result, items.size());
        return Result.success(result);
    }

    @PostMapping
    public Result<?> add(@RequestBody CartItem cartItem, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Product product = productMapper.selectById(cartItem.getProductId());
        if (product == null || product.getStatus() != 1) {
            throw new BusinessException("商品不存在或已下架");
        }
        if (isCartItemBlocked(product.getId())) {
            throw new BusinessException(SeckillService.CART_BLOCK_REASON);
        }

        CartItem existing = cartItemMapper.selectOne(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getProductId, cartItem.getProductId()));

        if (existing != null) {
            int addQty = normalizeRequestedQuantity(cartItem.getQuantity());
            int newQty = existing.getQuantity() + addQty;
            int maxQty = resolveMaxCartItemQuantity(product);
            if (newQty > maxQty) {
                throw new BusinessException("单个商品最多加入 " + maxQty + " 件");
            }
            if (newQty > product.getStock()) {
                throw new BusinessException("库存不足，当前库存: " + product.getStock());
            }
            ensureCartTotalQuantityLimit(userId, addQty);
            existing.setQuantity(newQty);
            cartItemMapper.updateById(existing);
            return Result.success("已更新购物车数量", existing);
        }

        int qty = normalizeRequestedQuantity(cartItem.getQuantity());
        int maxQty = resolveMaxCartItemQuantity(product);
        if (qty > maxQty) {
            throw new BusinessException("单个商品最多加入 " + maxQty + " 件");
        }
        if (qty > product.getStock()) {
            throw new BusinessException("库存不足，当前库存: " + product.getStock());
        }
        ensureCartDistinctLimit(userId);
        ensureCartTotalQuantityLimit(userId, qty);

        cartItem.setUserId(userId);
        cartItem.setQuantity(qty);
        cartItem.setSelected(1);
        cartItem.setId(null);
        cartItemMapper.insert(cartItem);
        return Result.success("加入购物车成功", cartItem);
    }

    @PutMapping("/{id}/quantity")
    public Result<?> updateQuantity(@PathVariable Long id, @RequestBody Map<String, Integer> params,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        CartItem item = cartItemMapper.selectById(id);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException("购物车项不存在");
        }
        Integer quantity = params.get("quantity");
        if (quantity == null || quantity < 1) {
            throw new BusinessException("数量不能小于1");
        }

        Product product = productMapper.selectById(item.getProductId());
        int maxQty = resolveMaxCartItemQuantity(product);
        if (quantity > maxQty) {
            throw new BusinessException("单个商品最多加入 " + maxQty + " 件");
        }
        if (product != null && quantity > product.getStock()) {
            throw new BusinessException("库存不足，当前库存: " + product.getStock());
        }
        if (isCartItemBlocked(item.getProductId())) {
            throw new BusinessException(SeckillService.CART_BLOCK_REASON);
        }

        int quantityDiff = quantity - item.getQuantity();
        if (quantityDiff > 0) {
            ensureCartTotalQuantityLimit(userId, quantityDiff);
        }

        item.setQuantity(quantity);
        cartItemMapper.updateById(item);
        return Result.success("更新成功");
    }

    @PutMapping("/{id}/selected")
    public Result<?> toggleSelected(@PathVariable Long id, @RequestBody Map<String, Integer> params,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        CartItem item = cartItemMapper.selectById(id);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException("购物车项不存在");
        }
        if (isCartItemBlocked(item.getProductId())) {
            item.setSelected(0);
            cartItemMapper.updateById(item);
            throw new BusinessException(SeckillService.CART_BLOCK_REASON);
        }
        Integer selected = params.get("selected");
        item.setSelected(selected != null ? selected : (item.getSelected() == 1 ? 0 : 1));
        cartItemMapper.updateById(item);
        return Result.success("更新成功");
    }

    @PutMapping("/selectAll")
    public Result<?> selectAll(@RequestBody Map<String, Integer> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Integer selected = params.getOrDefault("selected", 1);
        List<CartItem> items = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
        items.forEach(item -> {
            item.setSelected(isCartItemBlocked(item.getProductId()) ? 0 : selected);
            cartItemMapper.updateById(item);
        });
        return Result.success("操作成功");
    }

    @DeleteMapping("/{id}")
    public Result<?> remove(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        CartItem item = cartItemMapper.selectById(id);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException("购物车项不存在");
        }
        cartItemMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @DeleteMapping("/selected")
    public Result<?> removeSelected(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        cartItemMapper.delete(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSelected, 1));
        return Result.success("已清除选中商品");
    }

    @GetMapping("/count")
    public Result<?> count(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        long count = cartItemMapper.selectCount(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
        return Result.success(count);
    }

    @GetMapping("/checkout-preview")
    public Result<?> checkoutPreview(
            @RequestParam(required = false) Long userCouponId,
            @RequestParam(required = false, defaultValue = "true") Boolean applyBestSplitPlan,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        List<CartItem> selectedItems = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSelected, 1));
        if (selectedItems.isEmpty()) {
            throw new BusinessException("请先选择要结算的商品");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Map<String, Object>> itemDetails = new ArrayList<>();
        Set<Long> merchantIds = new HashSet<>();
        Map<Long, BigDecimal> merchantAmountMap = new LinkedHashMap<>();

        for (CartItem item : selectedItems) {
            if (isCartItemBlocked(item.getProductId())) {
                throw new BusinessException(SeckillService.CART_BLOCK_REASON);
            }
            Product p = productMapper.selectById(item.getProductId());
            if (p == null || p.getStatus() != 1) {
                throw new BusinessException("商品已下架或不可用，请返回购物车重新选择");
            }
            BigDecimal subtotal = p.getPrice().multiply(new BigDecimal(item.getQuantity()));
            totalAmount = totalAmount.add(subtotal);
            merchantIds.add(p.getMerchantId());
            merchantAmountMap.merge(p.getMerchantId(), subtotal, BigDecimal::add);

            Map<String, Object> detail = new HashMap<>();
            detail.put("cartItemId", item.getId());
            detail.put("productId", p.getId());
            detail.put("productName", p.getName());
            detail.put("productImage", p.getImage());
            detail.put("merchantId", p.getMerchantId());
            detail.put("price", p.getPrice());
            detail.put("quantity", item.getQuantity());
            detail.put("subtotal", subtotal);
            detail.put("stockEnough", p.getStock() >= item.getQuantity());
            itemDetails.add(detail);
        }

        if (itemDetails.isEmpty()) {
            throw new BusinessException("结算商品不可用，请返回购物车重试");
        }

        boolean crossMerchantCheckout = merchantIds.size() > 1;
        Long currentMerchantId = crossMerchantCheckout ? null : merchantIds.iterator().next();
        LocalDateTime now = LocalDateTime.now();
        int autoClaimedCouponCount = 0;
        if (userCouponId == null) {
            if (crossMerchantCheckout) {
                for (Map.Entry<Long, BigDecimal> entry : merchantAmountMap.entrySet()) {
                    autoClaimedCouponCount += autoClaimCouponsForCurrentOrder(
                            userId, normalizeMoney(entry.getValue()), entry.getKey(), now);
                }
            } else {
                autoClaimedCouponCount = autoClaimCouponsForCurrentOrder(userId, totalAmount, currentMerchantId, now);
            }
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        Map<String, Object> couponInfo = null;
        List<Map<String, Object>> availableCoupons = Collections.emptyList();
        Map<String, Object> selectedSplitPlan = null;
        Map<String, Long> selectedSplitCoupons = Collections.emptyMap();
        List<Map<String, Object>> splitCouponPlans = Collections.emptyList();

        if (crossMerchantCheckout) {
            if (userCouponId != null) {
                throw new BusinessException("跨店结算请使用AI推荐优惠方案后再提交");
            }
            List<UserCoupon> usableCoupons = loadUnusedUserCoupons(userId);
            Map<String, Object> crossPlanResult = buildCrossMerchantCouponOptimization(
                    usableCoupons, merchantAmountMap, normalizeMoney(totalAmount), now);
            Map<String, Object> bestSplitPlan = asMap(crossPlanResult.get("bestPlan"));
            Map<String, Object> noCouponPlan = asMap(crossPlanResult.get("noCouponPlan"));
            splitCouponPlans = asListOfMaps(crossPlanResult.get("plans"));
            boolean shouldApplyBestSplitPlan = applyBestSplitPlan == null || applyBestSplitPlan;
            selectedSplitPlan = shouldApplyBestSplitPlan ? bestSplitPlan : noCouponPlan;
            selectedSplitCoupons = toLongValueMap(selectedSplitPlan.get("splitCoupons"));
            discountAmount = money(selectedSplitPlan.get("discountAmount"));
            if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                couponInfo = new LinkedHashMap<>();
                couponInfo.put("couponName", selectedSplitPlan.getOrDefault("couponName", "跨店最优券组合"));
                couponInfo.put("scopeType", Constants.CouponScope.PLATFORM);
                couponInfo.put("scopeLabel", selectedSplitPlan.getOrDefault("scopeLabel", "跨店智能拆单"));
                couponInfo.put("discountAmount", discountAmount);
                couponInfo.put("planType", selectedSplitPlan.get("planType"));
                couponInfo.put("couponCount", selectedSplitPlan.get("couponCount"));
            }
        } else {
            if (userCouponId != null) {
                UserCoupon uc = userCouponMapper.selectById(userCouponId);
                if (uc == null || !uc.getUserId().equals(userId) || uc.getStatus() != 0) {
                    throw new BusinessException("所选优惠券不可用，请重新选择");
                }
                Coupon coupon = couponMapper.selectById(uc.getCouponId());
                if (coupon == null) {
                    throw new BusinessException("所选优惠券不存在，请重新选择");
                }

                if (coupon.getEndTime() == null || now.isAfter(coupon.getEndTime())) {
                    throw new BusinessException("所选优惠券已过期，请重新选择");
                }
                if (coupon.getStartTime() == null || now.isBefore(coupon.getStartTime())) {
                    throw new BusinessException("所选优惠券尚未开始，请重新选择");
                }

                Integer scopeType = coupon.getScopeType() == null
                        ? com.ecommerce.common.Constants.CouponScope.PLATFORM
                        : coupon.getScopeType();
                if (scopeType == com.ecommerce.common.Constants.CouponScope.MERCHANT_STORE
                        && !Objects.equals(coupon.getMerchantId(), currentMerchantId)) {
                    throw new BusinessException("店铺券仅可用于指定店铺订单");
                }
                BigDecimal minAmount = coupon.getMinAmount() == null ? BigDecimal.ZERO : coupon.getMinAmount();
                if (totalAmount.compareTo(minAmount) < 0) {
                    throw new BusinessException("未达到该优惠券使用门槛");
                }

                discountAmount = CouponUtil.calculateDiscount(coupon, totalAmount);
                couponInfo = new HashMap<>();
                couponInfo.put("userCouponId", uc.getId());
                couponInfo.put("couponId", coupon.getId());
                couponInfo.put("couponName", coupon.getName());
                couponInfo.put("scopeType", scopeType);
                couponInfo.put("scopeLabel", scopeType == com.ecommerce.common.Constants.CouponScope.MERCHANT_STORE
                        ? "店铺券（仅本店）"
                        : "平台券");
                couponInfo.put("discountAmount", discountAmount);
            }
            List<UserCoupon> usableCoupons = loadUnusedUserCoupons(userId);
            availableCoupons = buildAvailableCoupons(usableCoupons, totalAmount, currentMerchantId, now);
        }

        BigDecimal finalAmount = totalAmount.subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", itemDetails);
        result.put("itemCount", selectedItems.size());
        result.put("totalAmount", totalAmount);
        result.put("discountAmount", discountAmount);
        result.put("finalAmount", finalAmount);
        result.put("selectedCoupon", couponInfo);
        result.put("availableCoupons", availableCoupons);
        result.put("shippingFee", BigDecimal.ZERO);
        result.put("merchantId", currentMerchantId);
        result.put("crossMerchantCheckout", crossMerchantCheckout);
        result.put("selectedSplitPlan", selectedSplitPlan);
        result.put("selectedSplitCoupons", selectedSplitCoupons);
        result.put("recommendationSplitCoupons", selectedSplitCoupons);
        result.put("splitCouponPlans", splitCouponPlans);
        result.put("merchantAmounts", toMerchantAmountList(merchantAmountMap));
        result.put("autoClaimedCouponCount", autoClaimedCouponCount);

        return Result.success(result);
    }

    @GetMapping("/smart-guide")
    public Result<?> smartGuide(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        List<CartItem> selectedItems = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSelected, 1));
        if (selectedItems.isEmpty()) {
            throw new BusinessException("请先选择要结算的商品");
        }
        String selectedSignature = buildSmartGuideSelectedSignature(selectedItems);
        String cacheKey = buildSmartGuideCacheKey(userId, selectedSignature);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Map<String, Object>> itemDetails = new ArrayList<>();
        Set<Long> merchantIds = new HashSet<>();
        Map<Long, BigDecimal> merchantAmountMap = new LinkedHashMap<>();

        for (CartItem item : selectedItems) {
            if (isCartItemBlocked(item.getProductId())) {
                throw new BusinessException(SeckillService.CART_BLOCK_REASON);
            }
            Product p = productMapper.selectById(item.getProductId());
            if (p == null || p.getStatus() != 1) {
                throw new BusinessException("商品已下架或不可用，请返回购物车重新选择");
            }

            BigDecimal subtotal = p.getPrice().multiply(new BigDecimal(item.getQuantity()));
            totalAmount = totalAmount.add(subtotal);
            merchantIds.add(p.getMerchantId());
            merchantAmountMap.merge(p.getMerchantId(), subtotal, BigDecimal::add);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("cartItemId", item.getId());
            detail.put("productId", p.getId());
            detail.put("productName", p.getName());
            detail.put("productImage", p.getImage());
            detail.put("merchantId", p.getMerchantId());
            detail.put("price", normalizeMoney(p.getPrice()));
            detail.put("quantity", item.getQuantity());
            detail.put("subtotal", normalizeMoney(subtotal));
            detail.put("stockEnough", p.getStock() >= item.getQuantity());
            itemDetails.add(detail);
        }

        boolean crossMerchantCheckout = merchantIds.size() > 1;
        Long currentMerchantId = crossMerchantCheckout ? null : merchantIds.iterator().next();
        LocalDateTime now = LocalDateTime.now();
        int autoClaimedCouponCount = 0;
        if (crossMerchantCheckout) {
            for (Map.Entry<Long, BigDecimal> entry : merchantAmountMap.entrySet()) {
                autoClaimedCouponCount += autoClaimCouponsForCurrentOrder(
                        userId, normalizeMoney(entry.getValue()), entry.getKey(), now);
            }
        } else {
            autoClaimedCouponCount = autoClaimCouponsForCurrentOrder(userId, totalAmount, currentMerchantId, now);
        }

        if (autoClaimedCouponCount == 0 && redisUtil != null) {
            Object cached = redisUtil.get(cacheKey);
            if (cached instanceof Map<?, ?>) {
                return Result.success(cached);
            }
        }

        List<UserCoupon> usableCoupons = loadUnusedUserCoupons(userId);
        List<Map<String, Object>> availableCouponPlans = crossMerchantCheckout
                ? Collections.emptyList()
                : buildAvailableCouponPlans(usableCoupons, totalAmount, currentMerchantId);
        List<Map<String, Object>> splitCouponPlans = crossMerchantCheckout
                ? asListOfMaps(buildCrossMerchantCouponOptimization(
                        usableCoupons, merchantAmountMap, normalizeMoney(totalAmount), now).get("plans"))
                : Collections.emptyList();

        Map<String, Object> noCouponPlan = new LinkedHashMap<>();
        noCouponPlan.put("planType", "NO_COUPON");
        noCouponPlan.put("planLabel", "不使用优惠券");
        noCouponPlan.put("userCouponId", null);
        noCouponPlan.put("couponId", null);
        noCouponPlan.put("couponName", null);
        noCouponPlan.put("scopeType", Constants.CouponScope.PLATFORM);
        noCouponPlan.put("scopeLabel", crossMerchantCheckout ? "跨店合并结算" : "平台券");
        noCouponPlan.put("couponType", null);
        noCouponPlan.put("value", null);
        noCouponPlan.put("minAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        noCouponPlan.put("merchantId", currentMerchantId);
        noCouponPlan.put("discountAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        noCouponPlan.put("finalAmount", normalizeMoney(totalAmount));
        noCouponPlan.put("savingsAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        noCouponPlan.put("reasonType", "baseline");
        noCouponPlan.put("sourceType", "coupon-optimizer-v1");
        noCouponPlan.put("modelVersion", "cart-smart-guide-v1");
        noCouponPlan.put("dataFreshness", "real-time");
        noCouponPlan.put("splitCoupons", Collections.emptyMap());
        noCouponPlan.put("couponCount", 0);
        noCouponPlan.put("isActive", false);

        List<Map<String, Object>> candidatePlans = new ArrayList<>();
        candidatePlans.add(noCouponPlan);
        if (crossMerchantCheckout && !splitCouponPlans.isEmpty()) {
            candidatePlans.addAll(splitCouponPlans);
        } else {
            candidatePlans.addAll(availableCouponPlans);
        }
        if (candidatePlans.isEmpty()) {
            candidatePlans.add(noCouponPlan);
        }
        candidatePlans.sort((a, b) -> {
            BigDecimal finalA = money(a.get("finalAmount"));
            BigDecimal finalB = money(b.get("finalAmount"));
            int cmp = finalA.compareTo(finalB);
            if (cmp != 0) {
                return cmp;
            }
            return money(b.get("discountAmount")).compareTo(money(a.get("discountAmount")));
        });

        Map<String, Object> bestPlan = candidatePlans.get(0);
        bestPlan.put("isActive", true);
        List<Map<String, Object>> alternativePlans = candidatePlans.stream()
                .filter(plan -> !Objects.equals(plan.get("planType"), bestPlan.get("planType"))
                        || !Objects.equals(plan.get("userCouponId"), bestPlan.get("userCouponId")))
                .limit(3)
                .collect(Collectors.toList());

        List<Map<String, Object>> topUpSuggestions = crossMerchantCheckout
                ? Collections.emptyList()
                : buildTopUpSuggestions(usableCoupons, totalAmount, currentMerchantId, money(bestPlan.get("finalAmount")));

        String aiGuideText = crossMerchantCheckout
                ? buildCrossMerchantGuideText(totalAmount, bestPlan, merchantAmountMap)
                : buildSmartGuideText(userId, totalAmount, bestPlan, alternativePlans, topUpSuggestions);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", itemDetails);
        result.put("itemCount", selectedItems.size());
        result.put("merchantId", currentMerchantId);
        result.put("crossMerchantCheckout", crossMerchantCheckout);
        result.put("totalAmount", normalizeMoney(totalAmount));
        result.put("bestPlan", bestPlan);
        result.put("alternativePlans", alternativePlans);
        result.put("availableCoupons", availableCouponPlans);
        result.put("splitCouponPlans", splitCouponPlans);
        result.put("topUpSuggestions", topUpSuggestions);
        result.put("smartGuideText", aiGuideText);
        result.put("recommendationApplied",
                Objects.equals(bestPlan.get("planType"), "COUPON")
                        || Objects.equals(bestPlan.get("planType"), "SPLIT_COUPON"));
        result.put("recommendationUserCouponId",
                crossMerchantCheckout ? null : bestPlan.get("userCouponId"));
        result.put("recommendationSplitCoupons",
                crossMerchantCheckout ? toLongValueMap(bestPlan.get("splitCoupons")) : Collections.emptyMap());
        result.put("merchantAmounts", toMerchantAmountList(merchantAmountMap));
        result.put("reasonType", "cart_savings");
        result.put("sourceType", "coupon-optimizer-v1");
        result.put("modelVersion", "cart-smart-guide-v1");
        result.put("dataFreshness", "real-time");
        result.put("generatedAt", LocalDateTime.now());
        result.put("autoClaimedCouponCount", autoClaimedCouponCount);

        if (redisUtil != null) {
            int ttlSeconds = resolveSmartGuideCacheTtlSeconds(selectedSignature);
            redisUtil.set(cacheKey, result, ttlSeconds, TimeUnit.SECONDS);
        }
        return Result.success(result);
    }

    private List<UserCoupon> loadUnusedUserCoupons(Long userId) {
        return userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 0));
    }

    private List<Map<String, Object>> buildAvailableCoupons(List<UserCoupon> usableCoupons,
                                                            BigDecimal totalAmount,
                                                            Long currentMerchantId,
                                                            LocalDateTime now) {
        if (usableCoupons == null || usableCoupons.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> availableCoupons = new ArrayList<>();
        for (UserCoupon userCoupon : usableCoupons) {
            Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            if (!isCouponValidForCurrentOrder(coupon, totalAmount, currentMerchantId, now)) {
                continue;
            }

            Integer scopeType = coupon.getScopeType() == null
                    ? Constants.CouponScope.PLATFORM
                    : coupon.getScopeType();
            BigDecimal discountAmount = normalizeMoney(CouponUtil.calculateDiscount(coupon, totalAmount));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userCouponId", userCoupon.getId());
            item.put("couponId", coupon.getId());
            item.put("couponName", coupon.getName());
            item.put("couponType", coupon.getType());
            item.put("value", coupon.getValue());
            item.put("minAmount", normalizeMoney(coupon.getMinAmount()));
            item.put("scopeType", scopeType);
            item.put("merchantId", coupon.getMerchantId());
            item.put("scopeLabel", scopeType == Constants.CouponScope.MERCHANT_STORE
                    ? "店铺券（仅本店）"
                    : "平台券");
            item.put("discountAmount", discountAmount);
            availableCoupons.add(item);
        }

        availableCoupons.sort((a, b) -> money(b.get("discountAmount")).compareTo(money(a.get("discountAmount"))));
        return availableCoupons;
    }

    private int autoClaimCouponsForCurrentOrder(Long userId,
                                                BigDecimal totalAmount,
                                                Long merchantId,
                                                LocalDateTime now) {
        if (!moduleSwitchService.isEnabled("coupon")
                || userId == null
                || userId <= 0
                || merchantId == null
                || totalAmount == null
                || now == null) {
            return 0;
        }

        Set<Long> claimedCouponIds = loadClaimedCouponIds(userId);
        List<Coupon> autoClaimCandidates = loadAutoClaimCandidates(
                userId, totalAmount, merchantId, now, claimedCouponIds);
        if (autoClaimCandidates.isEmpty()) {
            return 0;
        }

        int claimSuccessCount = 0;
        for (Coupon coupon : autoClaimCandidates) {
            Long userCouponId = tryClaimCouponSilently(userId, coupon);
            if (userCouponId != null) {
                claimSuccessCount++;
            }
        }
        return claimSuccessCount;
    }

    private Set<Long> loadClaimedCouponIds(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptySet();
        }
        return userCouponMapper.selectList(
                        new LambdaQueryWrapper<UserCoupon>()
                                .eq(UserCoupon::getUserId, userId)
                                .select(UserCoupon::getCouponId))
                .stream()
                .map(UserCoupon::getCouponId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<Coupon> loadAutoClaimCandidates(Long userId,
                                                 BigDecimal totalAmount,
                                                 Long merchantId,
                                                 LocalDateTime now,
                                                 Set<Long> claimedCouponIds) {
        List<Coupon> activeCoupons = couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, 1)
                        .le(Coupon::getStartTime, now)
                        .ge(Coupon::getEndTime, now)
                        .orderByDesc(Coupon::getCreateTime));
        if (activeCoupons == null || activeCoupons.isEmpty()) {
            return Collections.emptyList();
        }

        List<Coupon> eligibleCoupons = couponAudienceService.filterEligibleCoupons(userId, activeCoupons);
        if (eligibleCoupons == null || eligibleCoupons.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> claimed = claimedCouponIds == null ? Collections.emptySet() : claimedCouponIds;
        return eligibleCoupons.stream()
                .filter(Objects::nonNull)
                .filter(coupon -> coupon.getId() != null && !claimed.contains(coupon.getId()))
                .filter(coupon -> isCouponValidForCurrentOrder(coupon, totalAmount, merchantId, now))
                .sorted((a, b) -> {
                    BigDecimal discountA = normalizeMoney(CouponUtil.calculateDiscount(a, totalAmount));
                    BigDecimal discountB = normalizeMoney(CouponUtil.calculateDiscount(b, totalAmount));
                    int cmp = discountB.compareTo(discountA);
                    if (cmp != 0) {
                        return cmp;
                    }
                    LocalDateTime endA = a.getEndTime();
                    LocalDateTime endB = b.getEndTime();
                    if (endA == null && endB == null) {
                        return 0;
                    }
                    if (endA == null) {
                        return 1;
                    }
                    if (endB == null) {
                        return -1;
                    }
                    return endA.compareTo(endB);
                })
                .limit(AUTO_CLAIM_MAX_COUNT)
                .collect(Collectors.toList());
    }

    private Long tryClaimCouponSilently(Long userId, Coupon coupon) {
        if (coupon == null || coupon.getId() == null || userId == null || userId <= 0) {
            return null;
        }

        UserCoupon existed = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getCouponId, coupon.getId())
                        .last("LIMIT 1"));
        if (existed != null) {
            return existed.getId();
        }

        int affected = couponMapper.incrementUsedCount(coupon.getId());
        if (affected == 0) {
            return null;
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setStatus(0);
        try {
            userCouponMapper.insert(userCoupon);
            return userCoupon.getId();
        } catch (DuplicateKeyException duplicateKeyException) {
            couponMapper.decrementUsedCountByAmount(coupon.getId(), 1);
            UserCoupon duplicated = userCouponMapper.selectOne(
                    new LambdaQueryWrapper<UserCoupon>()
                            .eq(UserCoupon::getUserId, userId)
                            .eq(UserCoupon::getCouponId, coupon.getId())
                            .last("LIMIT 1"));
            return duplicated == null ? null : duplicated.getId();
        } catch (Exception exception) {
            couponMapper.decrementUsedCountByAmount(coupon.getId(), 1);
            log.warn("[结算自动领券] userId={}, couponId={}, err={}", userId, coupon.getId(), exception.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> buildAvailableCouponPlans(List<UserCoupon> userCoupons,
                                                                BigDecimal totalAmount,
                                                                Long currentMerchantId) {
        if (userCoupons == null || userCoupons.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> plans = new ArrayList<>();
        for (UserCoupon userCoupon : userCoupons) {
            Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            if (!isCouponValidForCurrentOrder(coupon, totalAmount, currentMerchantId, now)) {
                continue;
            }
            Integer scopeType = coupon.getScopeType() == null ? Constants.CouponScope.PLATFORM : coupon.getScopeType();
            BigDecimal discountAmount = normalizeMoney(CouponUtil.calculateDiscount(coupon, totalAmount));
            BigDecimal finalAmount = normalizeMoney(totalAmount.subtract(discountAmount).max(BigDecimal.ZERO));

            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("planType", "COUPON");
            plan.put("planLabel", "使用优惠券");
            plan.put("userCouponId", userCoupon.getId());
            plan.put("couponId", coupon.getId());
            plan.put("couponName", coupon.getName());
            plan.put("couponType", coupon.getType());
            plan.put("value", coupon.getValue());
            plan.put("minAmount", normalizeMoney(coupon.getMinAmount()));
            plan.put("scopeType", scopeType);
            plan.put("scopeLabel", scopeType == Constants.CouponScope.MERCHANT_STORE ? "店铺券（仅本店）" : "平台券");
            plan.put("merchantId", coupon.getMerchantId());
            plan.put("discountAmount", discountAmount);
            plan.put("finalAmount", finalAmount);
            plan.put("savingsAmount", discountAmount);
            plan.put("reasonType", "coupon_discount");
            plan.put("sourceType", "coupon-optimizer-v1");
            plan.put("modelVersion", "cart-smart-guide-v1");
            plan.put("dataFreshness", "real-time");
            plans.add(plan);
        }

        plans.sort((a, b) -> {
            BigDecimal finalA = money(a.get("finalAmount"));
            BigDecimal finalB = money(b.get("finalAmount"));
            int cmp = finalA.compareTo(finalB);
            if (cmp != 0) {
                return cmp;
            }
            return money(b.get("discountAmount")).compareTo(money(a.get("discountAmount")));
        });
        return plans;
    }

    private List<Map<String, Object>> buildTopUpSuggestions(List<UserCoupon> userCoupons,
                                                            BigDecimal totalAmount,
                                                            Long merchantId,
                                                            BigDecimal currentBestFinalAmount) {
        if (userCoupons == null || userCoupons.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (UserCoupon userCoupon : userCoupons) {
            Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            if (coupon == null || !isCouponTimeValid(coupon, now) || !isCouponScopeMatched(coupon, merchantId)) {
                continue;
            }
            BigDecimal minAmount = normalizeMoney(coupon.getMinAmount());
            if (minAmount.compareTo(totalAmount) <= 0) {
                continue;
            }

            BigDecimal amountGap = normalizeMoney(minAmount.subtract(totalAmount));
            Product topUpProduct = findTopUpProduct(merchantId, amountGap);
            if (topUpProduct == null || topUpProduct.getPrice() == null) {
                continue;
            }

            BigDecimal topUpAmount = normalizeMoney(topUpProduct.getPrice());
            BigDecimal estimatedOrderAmount = normalizeMoney(totalAmount.add(topUpAmount));
            BigDecimal estimatedDiscount = normalizeMoney(CouponUtil.calculateDiscount(coupon, estimatedOrderAmount));
            BigDecimal estimatedFinalAmount = normalizeMoney(
                    estimatedOrderAmount.subtract(estimatedDiscount).max(BigDecimal.ZERO));
            BigDecimal extraSavings = normalizeMoney(totalAmount.subtract(estimatedFinalAmount));
            BigDecimal compareSavings = normalizeMoney(currentBestFinalAmount.subtract(estimatedFinalAmount));
            if (extraSavings.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("userCouponId", userCoupon.getId());
            suggestion.put("couponId", coupon.getId());
            suggestion.put("couponName", coupon.getName());
            suggestion.put("amountGap", amountGap);
            suggestion.put("recommendedTopUpAmount", topUpAmount);
            suggestion.put("estimatedOrderAmount", estimatedOrderAmount);
            suggestion.put("estimatedDiscountAmount", estimatedDiscount);
            suggestion.put("estimatedFinalAmount", estimatedFinalAmount);
            suggestion.put("extraSavingsVsNoCoupon", extraSavings);
            suggestion.put("extraSavingsVsBestPlan", compareSavings);
            suggestion.put("isBetterThanBestPlan", compareSavings.compareTo(BigDecimal.ZERO) > 0);
            suggestion.put("topUpProduct", toTopUpProduct(topUpProduct));
            suggestion.put("reasonType", "threshold_topup");
            suggestion.put("sourceType", "coupon-optimizer-v1");
            suggestion.put("modelVersion", "cart-smart-guide-v1");
            suggestion.put("dataFreshness", "real-time");
            suggestions.add(suggestion);
        }

        suggestions.sort((a, b) -> {
            BigDecimal savingA = money(a.get("extraSavingsVsNoCoupon"));
            BigDecimal savingB = money(b.get("extraSavingsVsNoCoupon"));
            int cmp = savingB.compareTo(savingA);
            if (cmp != 0) {
                return cmp;
            }
            return money(a.get("amountGap")).compareTo(money(b.get("amountGap")));
        });

        return suggestions.stream().limit(3).collect(Collectors.toList());
    }

    private Product findTopUpProduct(Long merchantId, BigDecimal amountGap) {
        List<Product> candidates = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)
                        .eq(Product::getMerchantId, merchantId)
                        .gt(Product::getStock, 0)
                        .orderByAsc(Product::getPrice)
                        .last("LIMIT 40"));
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        Product fallback = candidates.get(0);
        if (amountGap == null) {
            return fallback;
        }
        for (Product candidate : candidates) {
            if (candidate.getPrice() != null && candidate.getPrice().compareTo(amountGap) >= 0) {
                return candidate;
            }
        }
        return fallback;
    }

    private Map<String, Object> toTopUpProduct(Product product) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", product.getId());
        result.put("name", product.getName());
        result.put("image", product.getImage());
        result.put("price", normalizeMoney(product.getPrice()));
        result.put("merchantId", product.getMerchantId());
        return result;
    }

    private boolean isCouponValidForCurrentOrder(Coupon coupon,
                                                 BigDecimal totalAmount,
                                                 Long merchantId,
                                                 LocalDateTime now) {
        if (coupon == null) {
            return false;
        }
        if (!isCouponTimeValid(coupon, now)) {
            return false;
        }
        if (!isCouponScopeMatched(coupon, merchantId)) {
            return false;
        }
        BigDecimal minAmount = normalizeMoney(coupon.getMinAmount());
        return totalAmount.compareTo(minAmount) >= 0;
    }

    private boolean isCouponTimeValid(Coupon coupon, LocalDateTime now) {
        if (coupon == null || coupon.getStartTime() == null || coupon.getEndTime() == null || now == null) {
            return false;
        }
        return !now.isBefore(coupon.getStartTime()) && !now.isAfter(coupon.getEndTime());
    }

    private boolean isCouponScopeMatched(Coupon coupon, Long merchantId) {
        if (coupon == null) {
            return false;
        }
        Integer scopeType = coupon.getScopeType() == null ? Constants.CouponScope.PLATFORM : coupon.getScopeType();
        if (scopeType != Constants.CouponScope.MERCHANT_STORE) {
            return true;
        }
        return Objects.equals(coupon.getMerchantId(), merchantId);
    }

    private Map<String, Object> buildCrossMerchantCouponOptimization(List<UserCoupon> userCoupons,
                                                                     Map<Long, BigDecimal> merchantAmountMap,
                                                                     BigDecimal totalAmount,
                                                                     LocalDateTime now) {
        Map<String, Object> noCouponPlan = createCrossMerchantNoCouponPlan(totalAmount, merchantAmountMap);
        if (userCoupons == null || userCoupons.isEmpty() || merchantAmountMap == null || merchantAmountMap.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("noCouponPlan", noCouponPlan);
            result.put("bestPlan", noCouponPlan);
            result.put("plans", Collections.singletonList(noCouponPlan));
            return result;
        }

        List<Long> merchantIds = new ArrayList<>(merchantAmountMap.keySet());
        List<CrossMerchantCouponCandidate> candidates = new ArrayList<>();
        for (UserCoupon userCoupon : userCoupons) {
            if (userCoupon == null || userCoupon.getId() == null) {
                continue;
            }
            Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            if (coupon == null || !isCouponTimeValid(coupon, now)) {
                continue;
            }
            Map<Long, BigDecimal> eligibleDiscounts = new LinkedHashMap<>();
            for (Long merchantId : merchantIds) {
                if (!isCouponScopeMatched(coupon, merchantId)) {
                    continue;
                }
                BigDecimal merchantAmount = normalizeMoney(merchantAmountMap.get(merchantId));
                BigDecimal minAmount = normalizeMoney(coupon.getMinAmount());
                if (merchantAmount.compareTo(minAmount) < 0) {
                    continue;
                }
                BigDecimal discountAmount = normalizeMoney(CouponUtil.calculateDiscount(coupon, merchantAmount));
                if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                eligibleDiscounts.put(merchantId, discountAmount);
            }
            if (!eligibleDiscounts.isEmpty()) {
                candidates.add(new CrossMerchantCouponCandidate(userCoupon, coupon, eligibleDiscounts));
            }
        }

        if (candidates.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("noCouponPlan", noCouponPlan);
            result.put("bestPlan", noCouponPlan);
            result.put("plans", Collections.singletonList(noCouponPlan));
            return result;
        }

        CrossMerchantPlanState optimized = merchantIds.size() > 12
                ? optimizeCrossMerchantCouponsGreedy(candidates, merchantAmountMap)
                : optimizeCrossMerchantCouponsDp(candidates, merchantIds, merchantAmountMap, 0, 0, new HashMap<>());

        Map<String, Object> bestPlan = optimized == null || optimized.totalDiscount.compareTo(BigDecimal.ZERO) <= 0
                ? noCouponPlan
                : toCrossMerchantPlan(optimized, merchantAmountMap, totalAmount);

        List<Map<String, Object>> plans = new ArrayList<>();
        plans.add(bestPlan);
        if (!Objects.equals(bestPlan.get("planType"), "NO_COUPON")) {
            plans.add(noCouponPlan);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noCouponPlan", noCouponPlan);
        result.put("bestPlan", bestPlan);
        result.put("plans", plans);
        return result;
    }

    private CrossMerchantPlanState optimizeCrossMerchantCouponsDp(List<CrossMerchantCouponCandidate> candidates,
                                                                  List<Long> merchantIds,
                                                                  Map<Long, BigDecimal> merchantAmountMap,
                                                                  int index,
                                                                  int usedMask,
                                                                  Map<String, CrossMerchantPlanState> memo) {
        if (index >= candidates.size()) {
            return CrossMerchantPlanState.empty();
        }
        String cacheKey = index + ":" + usedMask;
        if (memo.containsKey(cacheKey)) {
            return memo.get(cacheKey);
        }

        CrossMerchantPlanState best = optimizeCrossMerchantCouponsDp(
                candidates, merchantIds, merchantAmountMap, index + 1, usedMask, memo);

        CrossMerchantCouponCandidate candidate = candidates.get(index);
        for (Map.Entry<Long, BigDecimal> entry : candidate.eligibleDiscounts.entrySet()) {
            int merchantIndex = merchantIds.indexOf(entry.getKey());
            if (merchantIndex < 0) {
                continue;
            }
            int bit = 1 << merchantIndex;
            if ((usedMask & bit) != 0) {
                continue;
            }
            CrossMerchantPlanState next = optimizeCrossMerchantCouponsDp(
                    candidates, merchantIds, merchantAmountMap, index + 1, usedMask | bit, memo);
            Map<Long, CrossMerchantCouponAllocation> allocations = new LinkedHashMap<>(next.allocations);
            allocations.put(entry.getKey(), new CrossMerchantCouponAllocation(
                    entry.getKey(),
                    candidate.userCoupon.getId(),
                    candidate.coupon.getId(),
                    candidate.coupon.getName(),
                    candidate.coupon.getScopeType() == null ? Constants.CouponScope.PLATFORM : candidate.coupon.getScopeType(),
                    candidate.coupon.getMerchantId(),
                    normalizeMoney(merchantAmountMap.get(entry.getKey())),
                    normalizeMoney(entry.getValue())
            ));
            CrossMerchantPlanState combined = new CrossMerchantPlanState(
                    normalizeMoney(next.totalDiscount.add(entry.getValue())),
                    allocations);
            if (isBetterCrossMerchantState(combined, best)) {
                best = combined;
            }
        }

        memo.put(cacheKey, best);
        return best;
    }

    private CrossMerchantPlanState optimizeCrossMerchantCouponsGreedy(List<CrossMerchantCouponCandidate> candidates,
                                                                      Map<Long, BigDecimal> merchantAmountMap) {
        List<CrossMerchantCouponAllocation> allAllocations = new ArrayList<>();
        for (CrossMerchantCouponCandidate candidate : candidates) {
            for (Map.Entry<Long, BigDecimal> entry : candidate.eligibleDiscounts.entrySet()) {
                allAllocations.add(new CrossMerchantCouponAllocation(
                        entry.getKey(),
                        candidate.userCoupon.getId(),
                        candidate.coupon.getId(),
                        candidate.coupon.getName(),
                        candidate.coupon.getScopeType() == null ? Constants.CouponScope.PLATFORM : candidate.coupon.getScopeType(),
                        candidate.coupon.getMerchantId(),
                        normalizeMoney(merchantAmountMap.get(entry.getKey())),
                        normalizeMoney(entry.getValue())
                ));
            }
        }
        allAllocations.sort((a, b) -> b.discountAmount.compareTo(a.discountAmount));
        Set<Long> usedMerchants = new HashSet<>();
        Set<Long> usedUserCoupons = new HashSet<>();
        Map<Long, CrossMerchantCouponAllocation> allocations = new LinkedHashMap<>();
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (CrossMerchantCouponAllocation allocation : allAllocations) {
            if (!usedMerchants.add(allocation.merchantId) || !usedUserCoupons.add(allocation.userCouponId)) {
                continue;
            }
            allocations.put(allocation.merchantId, allocation);
            totalDiscount = totalDiscount.add(allocation.discountAmount);
        }
        return new CrossMerchantPlanState(normalizeMoney(totalDiscount), allocations);
    }

    private boolean isBetterCrossMerchantState(CrossMerchantPlanState candidate,
                                               CrossMerchantPlanState currentBest) {
        if (candidate == null) {
            return false;
        }
        if (currentBest == null) {
            return true;
        }
        int cmp = candidate.totalDiscount.compareTo(currentBest.totalDiscount);
        if (cmp != 0) {
            return cmp > 0;
        }
        return candidate.allocations.size() > currentBest.allocations.size();
    }

    private Map<String, Object> toCrossMerchantPlan(CrossMerchantPlanState state,
                                                    Map<Long, BigDecimal> merchantAmountMap,
                                                    BigDecimal totalAmount) {
        if (state == null || state.totalDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return createCrossMerchantNoCouponPlan(totalAmount, merchantAmountMap);
        }
        List<Map<String, Object>> merchantPlans = new ArrayList<>();
        Map<String, Long> splitCoupons = new LinkedHashMap<>();
        int couponCount = 0;
        for (Map.Entry<Long, BigDecimal> entry : merchantAmountMap.entrySet()) {
            Long merchantId = entry.getKey();
            BigDecimal subtotal = normalizeMoney(entry.getValue());
            CrossMerchantCouponAllocation allocation = state.allocations.get(merchantId);
            BigDecimal discountAmount = allocation == null ? BigDecimal.ZERO : allocation.discountAmount;
            BigDecimal finalAmount = normalizeMoney(subtotal.subtract(discountAmount).max(BigDecimal.ZERO));
            Map<String, Object> merchantPlan = new LinkedHashMap<>();
            merchantPlan.put("merchantId", merchantId);
            merchantPlan.put("subtotal", subtotal);
            merchantPlan.put("discountAmount", discountAmount);
            merchantPlan.put("finalAmount", finalAmount);
            merchantPlan.put("userCouponId", allocation == null ? null : allocation.userCouponId);
            merchantPlan.put("couponId", allocation == null ? null : allocation.couponId);
            merchantPlan.put("couponName", allocation == null ? null : allocation.couponName);
            merchantPlan.put("scopeType", allocation == null ? Constants.CouponScope.PLATFORM : allocation.scopeType);
            merchantPlan.put("scopeLabel", allocation == null
                    ? "未使用优惠券"
                    : (allocation.scopeType == Constants.CouponScope.MERCHANT_STORE ? "店铺券（仅本店）" : "平台券"));
            merchantPlans.add(merchantPlan);
            if (allocation != null && allocation.userCouponId != null) {
                splitCoupons.put(String.valueOf(merchantId), allocation.userCouponId);
                couponCount++;
            }
        }

        BigDecimal totalDiscount = normalizeMoney(state.totalDiscount);
        BigDecimal finalAmount = normalizeMoney(totalAmount.subtract(totalDiscount).max(BigDecimal.ZERO));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planType", "SPLIT_COUPON");
        plan.put("planLabel", "跨店最优拆单券");
        plan.put("planKey", "split-best");
        plan.put("userCouponId", null);
        plan.put("couponId", null);
        plan.put("couponName", "跨店最优券组合");
        plan.put("scopeType", Constants.CouponScope.PLATFORM);
        plan.put("scopeLabel", couponCount > 0 ? "跨店智能拆单" : "跨店合并结算");
        plan.put("merchantId", null);
        plan.put("discountAmount", totalDiscount);
        plan.put("finalAmount", finalAmount);
        plan.put("savingsAmount", totalDiscount);
        plan.put("couponCount", couponCount);
        plan.put("splitCoupons", splitCoupons);
        plan.put("merchantPlans", merchantPlans);
        plan.put("reasonType", "cross_merchant_coupon_optimizer");
        plan.put("sourceType", "coupon-optimizer-v1");
        plan.put("modelVersion", "cart-smart-guide-v2");
        plan.put("dataFreshness", "real-time");
        return plan;
    }

    private Map<String, Object> createCrossMerchantNoCouponPlan(BigDecimal totalAmount,
                                                                Map<Long, BigDecimal> merchantAmountMap) {
        List<Map<String, Object>> merchantPlans = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : merchantAmountMap.entrySet()) {
            Map<String, Object> merchantPlan = new LinkedHashMap<>();
            merchantPlan.put("merchantId", entry.getKey());
            merchantPlan.put("subtotal", normalizeMoney(entry.getValue()));
            merchantPlan.put("discountAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            merchantPlan.put("finalAmount", normalizeMoney(entry.getValue()));
            merchantPlan.put("userCouponId", null);
            merchantPlan.put("couponId", null);
            merchantPlan.put("couponName", null);
            merchantPlan.put("scopeType", Constants.CouponScope.PLATFORM);
            merchantPlan.put("scopeLabel", "未使用优惠券");
            merchantPlans.add(merchantPlan);
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planType", "NO_COUPON");
        plan.put("planLabel", "不使用优惠券");
        plan.put("planKey", "split-none");
        plan.put("userCouponId", null);
        plan.put("couponId", null);
        plan.put("couponName", null);
        plan.put("scopeType", Constants.CouponScope.PLATFORM);
        plan.put("scopeLabel", "跨店合并结算");
        plan.put("merchantId", null);
        plan.put("discountAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        plan.put("finalAmount", normalizeMoney(totalAmount));
        plan.put("savingsAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        plan.put("couponCount", 0);
        plan.put("splitCoupons", Collections.emptyMap());
        plan.put("merchantPlans", merchantPlans);
        plan.put("reasonType", "baseline");
        plan.put("sourceType", "coupon-optimizer-v1");
        plan.put("modelVersion", "cart-smart-guide-v2");
        plan.put("dataFreshness", "real-time");
        return plan;
    }

    private String buildCrossMerchantGuideText(BigDecimal totalAmount,
                                               Map<String, Object> bestPlan,
                                               Map<Long, BigDecimal> merchantAmountMap) {
        int merchantCount = merchantAmountMap == null ? 0 : merchantAmountMap.size();
        BigDecimal discountAmount = money(bestPlan.get("discountAmount"));
        BigDecimal finalAmount = money(bestPlan.get("finalAmount"));
        int couponCount = Number.class.isInstance(bestPlan.get("couponCount"))
                ? ((Number) bestPlan.get("couponCount")).intValue()
                : 0;
        if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            return String.format("当前为跨店结算，系统已按 %d 个店铺自动拆单并匹配 %d 张最优优惠券，预计共省 %s 元，最终实付 %s 元。",
                    merchantCount,
                    couponCount,
                    discountAmount.toPlainString(),
                    finalAmount.toPlainString());
        }
        return String.format("当前为跨店结算，系统会自动拆成 %d 笔店铺订单提交。现阶段没有可直接生效的跨店优惠方案，建议先按当前价格结算。",
                merchantCount);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map<?, ?>) {
                result.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return result;
    }

    private Map<String, Long> toLongValueMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return Collections.emptyMap();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            try {
                result.put(String.valueOf(entry.getKey()), Long.valueOf(String.valueOf(entry.getValue())));
            } catch (NumberFormatException ignored) {
                // ignore invalid mapping
            }
        }
        return result;
    }

    private List<Map<String, Object>> toMerchantAmountList(Map<Long, BigDecimal> merchantAmountMap) {
        if (merchantAmountMap == null || merchantAmountMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : merchantAmountMap.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("merchantId", entry.getKey());
            item.put("subtotal", normalizeMoney(entry.getValue()));
            result.add(item);
        }
        return result;
    }

    private String buildSmartGuideText(Long userId,
                                       BigDecimal totalAmount,
                                       Map<String, Object> bestPlan,
                                       List<Map<String, Object>> alternatives,
                                       List<Map<String, Object>> topUpSuggestions) {
        String fallback = buildFallbackSmartGuideText(totalAmount, bestPlan, topUpSuggestions);
        if (aiService == null || !moduleSwitchService.isEnabled("ai-chat")) {
            return fallback;
        }

        String couponName = bestPlan.get("couponName") == null ? "无" : String.valueOf(bestPlan.get("couponName"));
        String prompt = "你是电商结算页导购，请用2-3句中文给出最省钱建议，不要输出列表或markdown。"
                + "当前总价：" + normalizeMoney(totalAmount)
                + "；最优方案：" + couponName
                + "；优惠：" + money(bestPlan.get("discountAmount"))
                + "；实付：" + money(bestPlan.get("finalAmount"))
                + "。若无券可用请提醒领券或凑单。";

        try {
            Map<String, Object> aiResult = aiService.shoppingAssistant(userId, prompt, Collections.emptyList());
            if (aiResult != null && aiResult.get("reply") instanceof String) {
                String aiText = String.valueOf(aiResult.get("reply")).trim();
                if (!aiText.isEmpty()) {
                    return aiText;
                }
            }
        } catch (Exception ignored) {
            // 忽略 AI 异常并回退规则文案
        }
        return fallback;
    }

    private String buildFallbackSmartGuideText(BigDecimal totalAmount,
                                               Map<String, Object> bestPlan,
                                               List<Map<String, Object>> topUpSuggestions) {
        String planType = String.valueOf(bestPlan.get("planType"));
        if ("COUPON".equals(planType)) {
            return String.format("建议优先使用%s，预计立减%s元，最终实付%s元。当前购物车总价%s元，这个方案是现阶段最省的结算方式。",
                    String.valueOf(bestPlan.get("couponName")),
                    money(bestPlan.get("discountAmount")).toPlainString(),
                    money(bestPlan.get("finalAmount")).toPlainString(),
                    normalizeMoney(totalAmount).toPlainString());
        }

        if (topUpSuggestions != null && !topUpSuggestions.isEmpty()) {
            Map<String, Object> suggestion = topUpSuggestions.get(0);
            Object productObj = suggestion.get("topUpProduct");
            String productName = "推荐商品";
            if (productObj instanceof Map<?, ?>) {
                Object name = ((Map<?, ?>) productObj).get("name");
                if (name != null) {
                    productName = String.valueOf(name);
                }
            }
            return String.format("当前暂无可直接使用的优惠券。可凑单约%s元（如%s）后使用%s，预计实付%s元。",
                    money(suggestion.get("amountGap")).toPlainString(),
                    productName,
                    String.valueOf(suggestion.get("couponName")),
                    money(suggestion.get("estimatedFinalAmount")).toPlainString());
        }

        return "当前购物车暂无可用优惠券，建议先去领券中心领取平台券或店铺券后再结算，通常可进一步降低实付金额。";
    }

    private String buildSmartGuideSelectedSignature(List<CartItem> selectedItems) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return "";
        }
        return selectedItems.stream()
                .map(item -> String.format("%s:%s:%s",
                        item.getId(),
                        item.getProductId(),
                        item.getQuantity()))
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private String buildSmartGuideCacheKey(Long userId, String selectedSignature) {
        String raw = String.format("%s#%s", userId == null ? 0L : userId, selectedSignature == null ? "" : selectedSignature);
        String md5 = DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        return SMART_GUIDE_CACHE_PREFIX + (userId == null ? 0L : userId) + ":" + md5;
    }

    private int resolveSmartGuideCacheTtlSeconds(String selectedSignature) {
        int range = SMART_GUIDE_CACHE_TTL_MAX_SECONDS - SMART_GUIDE_CACHE_TTL_MIN_SECONDS + 1;
        int hash = Math.abs((selectedSignature == null ? "" : selectedSignature).hashCode());
        return SMART_GUIDE_CACHE_TTL_MIN_SECONDS + (hash % range);
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof BigDecimal) {
            return normalizeMoney((BigDecimal) value);
        }
        try {
            return normalizeMoney(new BigDecimal(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private int normalizeRequestedQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            return 1;
        }
        return quantity;
    }

    private int resolveMaxCartItemQuantity(Product product) {
        int stockLimit = product != null && product.getStock() != null
                ? Math.max(1, product.getStock())
                : CART_ITEM_QUANTITY_LIMIT;
        return Math.min(CART_ITEM_QUANTITY_LIMIT, stockLimit);
    }

    private void normalizeCartItemQuantity(CartItem item, Product product) {
        if (item == null) {
            return;
        }
        int safeQuantity = Math.max(1, item.getQuantity() == null ? 1 : item.getQuantity());
        safeQuantity = Math.min(safeQuantity, resolveMaxCartItemQuantity(product));
        if (!Objects.equals(item.getQuantity(), safeQuantity)) {
            item.setQuantity(safeQuantity);
            cartItemMapper.updateById(item);
        }
    }

    private void ensureCartDistinctLimit(Long userId) {
        long distinctCount = countCartDistinctItems(userId);
        if (distinctCount >= CART_DISTINCT_ITEM_LIMIT) {
            throw new BusinessException(409, CART_DISTINCT_ITEM_LIMIT_MESSAGE);
        }
    }

    private void ensureCartTotalQuantityLimit(Long userId, int additionalQuantity) {
        List<CartItem> items = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId));
        int totalQuantity = items.stream().mapToInt(CartItem::getQuantity).sum();
        if (totalQuantity + additionalQuantity > CART_TOTAL_QUANTITY_LIMIT) {
            throw new BusinessException(409, CART_TOTAL_QUANTITY_LIMIT_MESSAGE);
        }
    }

    private long countCartDistinctItems(Long userId) {
        return cartItemMapper.selectCount(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId));
    }

    private void appendCartDistinctMeta(Map<String, Object> result, int distinctCount) {
        int safeDistinctCount = Math.max(0, distinctCount);
        int remainingDistinctCount = Math.max(CART_DISTINCT_ITEM_LIMIT - safeDistinctCount, 0);
        result.put("distinctCount", safeDistinctCount);
        result.put("distinctLimit", CART_DISTINCT_ITEM_LIMIT);
        result.put("remainingDistinctCount", remainingDistinctCount);
        result.put("distinctLimitReached", remainingDistinctCount == 0);
    }

    private static final class CrossMerchantCouponCandidate {
        private final UserCoupon userCoupon;
        private final Coupon coupon;
        private final Map<Long, BigDecimal> eligibleDiscounts;

        private CrossMerchantCouponCandidate(UserCoupon userCoupon,
                                             Coupon coupon,
                                             Map<Long, BigDecimal> eligibleDiscounts) {
            this.userCoupon = userCoupon;
            this.coupon = coupon;
            this.eligibleDiscounts = eligibleDiscounts;
        }
    }

    private static final class CrossMerchantCouponAllocation {
        private final Long merchantId;
        private final Long userCouponId;
        private final Long couponId;
        private final String couponName;
        private final Integer scopeType;
        private final Long scopeMerchantId;
        private final BigDecimal subtotal;
        private final BigDecimal discountAmount;

        private CrossMerchantCouponAllocation(Long merchantId,
                                              Long userCouponId,
                                              Long couponId,
                                              String couponName,
                                              Integer scopeType,
                                              Long scopeMerchantId,
                                              BigDecimal subtotal,
                                              BigDecimal discountAmount) {
            this.merchantId = merchantId;
            this.userCouponId = userCouponId;
            this.couponId = couponId;
            this.couponName = couponName;
            this.scopeType = scopeType;
            this.scopeMerchantId = scopeMerchantId;
            this.subtotal = subtotal;
            this.discountAmount = discountAmount;
        }
    }

    private static final class CrossMerchantPlanState {
        private final BigDecimal totalDiscount;
        private final Map<Long, CrossMerchantCouponAllocation> allocations;

        private CrossMerchantPlanState(BigDecimal totalDiscount,
                                       Map<Long, CrossMerchantCouponAllocation> allocations) {
            this.totalDiscount = totalDiscount == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : totalDiscount.setScale(2, RoundingMode.HALF_UP);
            this.allocations = allocations == null ? Collections.emptyMap() : allocations;
        }

        private static CrossMerchantPlanState empty() {
            return new CrossMerchantPlanState(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), new LinkedHashMap<>());
        }
    }

    private boolean isCartItemBlocked(Long productId) {
        if (!moduleSwitchService.isEnabled("seckill")) {
            return false;
        }
        return productId != null && seckillService.isActiveSeckillProduct(productId);
    }
}
