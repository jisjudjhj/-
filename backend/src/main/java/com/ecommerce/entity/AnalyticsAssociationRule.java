package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_association_rule")
public class AnalyticsAssociationRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate snapshotDate;

    private Long lhsProductId;

    private Long rhsProductId;

    private Long supportCount;

    private BigDecimal supportRate;

    private BigDecimal confidence;

    private BigDecimal lift;

    private Integer rankNo;

    private LocalDateTime createTime;
}
