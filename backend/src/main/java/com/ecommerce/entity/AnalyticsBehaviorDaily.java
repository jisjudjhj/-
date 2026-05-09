package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_behavior_daily")
public class AnalyticsBehaviorDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private String behaviorType;

    private Long userCount;

    private Long eventCount;

    private Long productCount;

    private BigDecimal avgDuration;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
