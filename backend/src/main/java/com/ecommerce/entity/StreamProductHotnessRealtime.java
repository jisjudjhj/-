package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stream_product_hotness_realtime")
public class StreamProductHotnessRealtime {

    @TableId(value = "product_id", type = IdType.INPUT)
    private Long productId;

    private Long categoryId;

    private Double hotScore;

    private Long behaviorCount;

    private Long purchaseCount;

    private LocalDateTime lastEventTime;

    private LocalDateTime updateTime;
}
