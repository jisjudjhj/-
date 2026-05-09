package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    List<Long> selectOwnedUserIds(@Param("couponId") Long couponId,
                                  @Param("userIds") Collection<Long> userIds);

    int batchInsertIgnore(@Param("list") List<UserCoupon> userCoupons);
}
