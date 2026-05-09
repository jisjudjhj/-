package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stream_user_category_preference")
public class StreamUserCategoryPreference {

    private Long userId;

    private Long categoryId;

    private String categoryName;

    private Double preferenceScore;

    private Long behaviorCount;

    private LocalDateTime lastEventTime;

    private LocalDateTime updateTime;
}
