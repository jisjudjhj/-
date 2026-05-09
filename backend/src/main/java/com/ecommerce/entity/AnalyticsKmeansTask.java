package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "analytics_kmeans_task", autoResultMap = true)
public class AnalyticsKmeansTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private LocalDate snapshotDate;

    private String status;

    private String algorithmName;

    private String modelVersion;

    private String featureVersion;

    private Integer clusterCount;

    private Long sampleUserCount;

    private Long clusteredUserCount;

    private Long coldStartUserCount;

    private BigDecimal silhouetteScore;

    private BigDecimal inertiaScore;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> featureColumns;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultSummary;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> llmOverview;

    private String errorMessage;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
