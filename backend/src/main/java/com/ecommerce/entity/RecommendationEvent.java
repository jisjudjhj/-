package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recommendation_event")
public class RecommendationEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private String eventType;

    private String scene;

    private String traceId;

    private String recommendationToken;

    private String experimentGroup;

    private Integer duration;

    private Long orderId;

    private BigDecimal amount;

    private LocalDateTime eventTime;

    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
