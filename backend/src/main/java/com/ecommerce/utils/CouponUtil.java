package com.ecommerce.utils;

import com.ecommerce.entity.Coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CouponUtil {

    private CouponUtil() {}

    public static BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        BigDecimal discount;
        switch (coupon.getType()) {
            case 1: // 满减券
                discount = coupon.getValue();
                break;
            case 2: // 折扣券
                discount = orderAmount.multiply(BigDecimal.ONE.subtract(
                        coupon.getValue().divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP)));
                if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
                    discount = coupon.getMaxDiscount();
                }
                break;
            case 3: // 无门槛券
                discount = coupon.getValue();
                break;
            default:
                discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }
}
