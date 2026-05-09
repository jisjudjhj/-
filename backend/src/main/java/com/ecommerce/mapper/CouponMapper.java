package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    @Update("UPDATE coupon SET used_count = used_count + 1 WHERE id = #{couponId} AND used_count < total_count")
    int incrementUsedCount(Long couponId);

    @Update("UPDATE coupon " +
            "SET used_count = used_count + #{amount} " +
            "WHERE id = #{couponId} " +
            "AND status = 1 " +
            "AND start_time <= NOW() " +
            "AND end_time >= NOW() " +
            "AND used_count + #{amount} <= total_count")
    int incrementUsedCountByAmount(@Param("couponId") Long couponId, @Param("amount") Integer amount);

    @Update("UPDATE coupon " +
            "SET used_count = CASE WHEN used_count >= #{amount} THEN used_count - #{amount} ELSE 0 END " +
            "WHERE id = #{couponId}")
    int decrementUsedCountByAmount(@Param("couponId") Long couponId, @Param("amount") Integer amount);
}
