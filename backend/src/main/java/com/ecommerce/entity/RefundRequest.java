package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "refund_request", autoResultMap = true)
public class RefundRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long userId;

    private String reason;

    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    private BigDecimal amount;

    /**
     * 0-待审核 1-已同意 2-已拒绝 3-已退款
     */
    private Integer status;

    private String rejectReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String orderNo;

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String interventionStatus;

    @TableField(exist = false)
    private String interventionReason;

    @TableField(exist = false)
    private String interventionTime;
}
