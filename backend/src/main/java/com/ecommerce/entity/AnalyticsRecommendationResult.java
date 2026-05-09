package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_recommendation_result")
public class AnalyticsRecommendationResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate snapshotDate;

    private String scene;

    private Long userId;

    private Long productId;

    private Integer rankNo;

    private BigDecimal score;

    private String algorithm;

    private String reason;

    private String modelVersion;

    private LocalDateTime createTime;
}
