package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.entity.Order;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    BigDecimal selectTotalRevenue();

    List<Map<String, Object>> selectRecentStats();

    int casUpdateStatus(@Param("id") Long id, @Param("oldStatus") int oldStatus, @Param("newStatus") int newStatus);

    List<Order> selectTimeoutPendingOrders(@Param("minutes") int minutes);

    Integer selectUserSeckillPurchasedQuantity(@Param("userId") Long userId, @Param("applyId") Long applyId);

    IPage<Order> selectMerchantOrderPage(IPage<Order> page,
                                         @Param("merchantId") Long merchantId,
                                         @Param("status") Integer status);
}
