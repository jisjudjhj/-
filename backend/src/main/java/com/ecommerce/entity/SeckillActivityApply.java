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
@TableName("seckill_activity_apply")
public class SeckillActivityApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long merchantId;

    private Long productId;

    private BigDecimal productPrice;

    private BigDecimal seckillPrice;

    private Integer seckillStock;

    private Integer soldCount;

    private Integer limitPerUser;

    /**
     * 0-待审核 1-通过 2-驳回 3-已撤回
     */
    private Integer auditStatus;

    private String rejectReason;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private SeckillActivity activity;

    @TableField(exist = false)
    private Product product;

    @TableField(exist = false)
    private String productName;

    @TableField(exist = false)
    private BigDecimal originalPrice;

    @TableField(exist = false)
    private String merchantName;

    @TableField(exist = false)
    private String activityName;

    @TableField(exist = false)
    private LocalDateTime activityStartTime;

    @TableField(exist = false)
    private LocalDateTime activityEndTime;

    @TableField(exist = false)
    private Integer publishStatus;
}
