package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_user_profile_snapshot")
public class AnalyticsUserProfileSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate snapshotDate;

    private Long userId;

    private Long totalBehaviors;

    private String categoryPreferences;

    private String tagPreferences;

    private BigDecimal priceRangeMin;

    private BigDecimal priceRangeMax;

    private Integer coldStart;

    private String modelVersion;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
