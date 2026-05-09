package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_report_snapshot")
public class AnalyticsReportSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate snapshotDate;

    private String reportCode;

    private String reportName;

    private String reportData;

    private String sourceTables;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
