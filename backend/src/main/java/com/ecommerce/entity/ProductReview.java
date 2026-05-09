package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "product_review", autoResultMap = true)
public class ProductReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private Long orderId;

    private Integer rating;

    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> videoUrls;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private String appendContent;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> appendImages;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> appendVideoUrls;

    private LocalDateTime appendTime;

    private Integer helpfulCount;

    private String reply;

    private LocalDateTime replyTime;

    /**
     * 0-待审核 1-已通过 2-已拒绝
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String avatar;

    @TableField(exist = false)
    private String productName;

    @TableField(exist = false)
    private Boolean helpfulVoted;
}
