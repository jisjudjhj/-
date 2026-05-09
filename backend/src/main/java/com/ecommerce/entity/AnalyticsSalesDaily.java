package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_sales_daily")
public class AnalyticsSalesDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private Integer isForecast;

    private Long paidOrderCount;

    private Long paidUserCount;

    private BigDecimal revenue;

    private BigDecimal refundAmount;

    private BigDecimal avgOrderValue;

    @TableField("moving_avg_7d")
    private BigDecimal movingAvg7d;

    private BigDecimal weekOverWeek;

    private BigDecimal forecastConfidence;

    private String modelVersion;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
