package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.entity.Coupon;
import com.ecommerce.entity.UserCoupon;
import com.ecommerce.mapper.CouponMapper;
import com.ecommerce.mapper.UserCouponMapper;
import com.ecommerce.service.CouponAudienceService;
import com.ecommerce.service.ModuleSwitchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/coupons")
@Tag(name = "Coupon", description = "优惠券领取、查询和使用相关接口")
public class CouponController {

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private CouponAudienceService couponAudienceService;

    @GetMapping("/has-unclaimed")
    @Operation(summary = "查询是否有待领取优惠券", description = "返回当前用户是否还有未领取的可领优惠券数量。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> hasUnclaimed(HttpServletRequest request) {
        if (!moduleSwitchService.isEnabled("coupon")) {
            return Result.success(buildUnclaimedResult(0));
        }

        Long userId = (Long) request.getAttribute("userId");
        List<Coupon> eligibleCoupons = loadEligibleActiveCoupons(userId);
        if (eligibleCoupons.isEmpty()) {
            return Result.success(buildUnclaimedResult(0));
        }

        Set<Long> claimedIds = loadClaimedCouponIds(userId);
        long unclaimedCount = eligibleCoupons.stream()
                .map(Coupon::getId)
                .filter(couponId -> couponId != null && !claimedIds.contains(couponId))
                .count();
        return Result.success(buildUnclaimedResult(unclaimedCount));
    }

    @GetMapping
    @Operation(summary = "获取可领取优惠券列表", description = "未登录默认查看公开券，登录后会结合分群和指定用户范围过滤。")
    public Result<?> availableCoupons(HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long userId = (Long) request.getAttribute("userId");

        List<Coupon> coupons = loadEligibleActiveCoupons(userId);
        Set<Long> claimedIds = loadClaimedCouponIds(userId);
        coupons.forEach(coupon -> coupon.setUserCouponStatus(
                coupon.getId() != null && claimedIds.contains(coupon.getId()) ? 1 : 0));
        return Result.success(coupons);
    }

    @PostMapping("/{couponId}/claim")
    @Transactional
    @Operation(summary = "领取优惠券", description = "领取指定优惠券，自动校验时间范围、库存、重复领取和定向发放条件。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> claimCoupon(@PathVariable Long couponId, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long userId = (Long) request.getAttribute("userId");

        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new BusinessException("优惠券不存在或已下架");
        }

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() == null
                || coupon.getEndTime() == null
                || now.isBefore(coupon.getStartTime())
                || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException("不在领取时间范围内");
        }

        if (!couponAudienceService.isEligibleForCoupon(coupon, userId)) {
            throw new BusinessException("当前用户不满足该优惠券的领取条件");
        }

        long existCount = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getCouponId, couponId));
        if (existCount > 0) {
            throw new BusinessException("您已领取过此优惠券");
        }

        int affected = couponMapper.incrementUsedCount(couponId);
        if (affected == 0) {
            throw new BusinessException("优惠券已领完");
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(0);
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("您已领取过此优惠券");
        }

        return Result.success("领取成功");
    }

    @GetMapping("/my")
    @Operation(summary = "获取我的优惠券", description = "按状态分页查询当前用户已经持有的优惠券。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> myCoupons(@RequestParam(defaultValue = "0") Integer status,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int size,
                               HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long userId = (Long) request.getAttribute("userId");

        IPage<UserCoupon> userCouponPage = userCouponMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, status)
                        .orderByDesc(UserCoupon::getCreateTime));

        userCouponPage.getRecords().forEach(userCoupon -> {
            Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            userCoupon.setCoupon(coupon);
        });
        return Result.success(userCouponPage);
    }

    @GetMapping("/usable")
    @Operation(summary = "获取当前订单可用优惠券", description = "根据订单金额筛选当前用户可使用的优惠券。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> usableCoupons(@RequestParam BigDecimal orderAmount,
                                   @RequestParam(required = false) Long merchantId,
                                   HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long userId = (Long) request.getAttribute("userId");
        LocalDateTime now = LocalDateTime.now();

        List<UserCoupon> userCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 0));

        List<UserCoupon> usableCoupons = userCoupons.stream()
                .filter(userCoupon -> {
                    Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
                    if (coupon == null
                            || coupon.getEndTime() == null
                            || now.isAfter(coupon.getEndTime())) {
                        return false;
                    }
                    Integer scopeType = coupon.getScopeType() == null
                            ? com.ecommerce.common.Constants.CouponScope.PLATFORM
                            : coupon.getScopeType();
                    if (scopeType == com.ecommerce.common.Constants.CouponScope.MERCHANT_STORE) {
                        if (merchantId == null || !merchantId.equals(coupon.getMerchantId())) {
                            return false;
                        }
                    }

                    userCoupon.setCoupon(coupon);
                    BigDecimal minAmount = coupon.getMinAmount() == null ? BigDecimal.ZERO : coupon.getMinAmount();
                    return orderAmount.compareTo(minAmount) >= 0;
                })
                .collect(Collectors.toList());
        return Result.success(usableCoupons);
    }

    @GetMapping("/calculate")
    @Operation(summary = "试算优惠金额", description = "根据用户已领取的优惠券和订单金额试算优惠后金额。")
    @SecurityRequirement(name = "BearerAuth")
    public Result<?> calculateDiscount(@RequestParam Long userCouponId,
                                       @RequestParam BigDecimal orderAmount,
                                       HttpServletRequest request) {
        moduleSwitchService.requireEnabled("coupon");
        Long userId = (Long) request.getAttribute("userId");

        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !userCoupon.getUserId().equals(userId) || userCoupon.getStatus() != 0) {
            throw new BusinessException("优惠券不可用");
        }

        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }

        BigDecimal minAmount = coupon.getMinAmount() == null ? BigDecimal.ZERO : coupon.getMinAmount();
        if (orderAmount.compareTo(minAmount) < 0) {
            throw new BusinessException("未达到使用门槛 ¥" + minAmount.toPlainString());
        }

        BigDecimal discount = com.ecommerce.utils.CouponUtil.calculateDiscount(coupon, orderAmount);
        Map<String, Object> result = new HashMap<>();
        result.put("discount", discount);
        result.put("finalAmount", orderAmount.subtract(discount));
        result.put("couponName", coupon.getName());
        return Result.success(result);
    }

    private List<Coupon> loadEligibleActiveCoupons(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> activeCoupons = couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, 1)
                        .le(Coupon::getStartTime, now)
                        .ge(Coupon::getEndTime, now)
                        .orderByDesc(Coupon::getCreateTime));
        if (activeCoupons == null || activeCoupons.isEmpty()) {
            return Collections.emptyList();
        }
        return couponAudienceService.filterEligibleCoupons(userId, activeCoupons);
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
                .filter(couponId -> couponId != null)
                .collect(Collectors.toSet());
    }

    private Map<String, Object> buildUnclaimedResult(long unclaimedCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("hasUnclaimed", unclaimedCount > 0);
        result.put("count", unclaimedCount);
        return result;
    }

    /**
     * @deprecated Use {@link com.ecommerce.utils.CouponUtil#calculateDiscount(Coupon, BigDecimal)} instead.
     */
    @Deprecated
    public static BigDecimal calculateCouponDiscount(Coupon coupon, BigDecimal orderAmount) {
        return com.ecommerce.utils.CouponUtil.calculateDiscount(coupon, orderAmount);
    }
}
