package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_funnel_daily")
public class AnalyticsFunnelDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private Long viewUserCount;

    private Long cartUserCount;

    private Long favoriteUserCount;

    private Long purchaseUserCount;

    private BigDecimal viewToCartRate;

    private BigDecimal cartToPurchaseRate;

    private BigDecimal viewToPurchaseRate;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
