package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_behavior")
public class UserBehavior {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private String behaviorType;

    private String searchKeyword;

    private Integer duration;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String productName;

    @TableField(exist = false)
    private Product product;

    @TableField(exist = false)
    private String recommendationToken;

    @TableField(exist = false)
    private String recommendationScene;

    @TableField(exist = false)
    private Long orderId;
}
