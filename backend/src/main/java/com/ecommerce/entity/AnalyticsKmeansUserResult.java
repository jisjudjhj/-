package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_kmeans_user_result")
public class AnalyticsKmeansUserResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private LocalDate snapshotDate;

    private Long userId;

    private String segmentCode;

    private String segmentName;

    private Integer clusterIndex;

    private BigDecimal distanceToCenter;

    private BigDecimal confidenceScore;

    private Integer isColdStart;

    private Integer sortOrder;

    private String personaSummary;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
