package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_rfm_user_snapshot")
public class AnalyticsRfmUserSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate snapshotDate;

    private Long userId;

    private Integer recencyDays;

    private Integer frequencyCount;

    private BigDecimal monetaryAmount;

    private Integer rScore;

    private Integer fScore;

    private Integer mScore;

    private String rfmCode;

    private String segmentName;

    private LocalDateTime createTime;
}
