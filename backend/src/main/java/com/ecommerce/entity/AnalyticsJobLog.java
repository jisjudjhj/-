package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "analytics_job_log", autoResultMap = true)
public class AnalyticsJobLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobName;

    private String batchNo;

    private String jobType;

    private String status;

    private LocalDate snapshotDate;

    private Long processedCount;

    private Long outputCount;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultSummary;

    private String errorMessage;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
