package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_product_similarity")
public class AnalyticsProductSimilarity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate snapshotDate;

    private Long productId;

    private Long similarProductId;

    private BigDecimal similarity;

    private String sourceAlgorithm;

    private Integer rankNo;

    private LocalDateTime createTime;
}
