package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_rfm_segment_snapshot")
public class AnalyticsRfmSegmentSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate snapshotDate;

    private String segmentName;

    private Long userCount;

    private BigDecimal percentage;

    private BigDecimal avgRecencyDays;

    private BigDecimal avgFrequency;

    private BigDecimal avgMonetary;

    private LocalDateTime createTime;
}
