package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("analytics_behavior_heatmap")
public class AnalyticsBehaviorHeatmap {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private Integer dayOfWeek;

    private Integer hourOfDay;

    private String behaviorType;

    private Long eventCount;

    private Long userCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
