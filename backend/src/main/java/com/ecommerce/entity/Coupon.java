package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 1-满减券 2-折扣券 3-无门槛券
     */
    private Integer type;

    private BigDecimal value;

    private BigDecimal minAmount;

    private BigDecimal maxDiscount;

    private Integer totalCount;

    private Integer usedCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 0-未开始 1-进行中 2-已结束
     */
    private Integer status;

    /**
     * 优惠券作用域: 0-平台通用券 1-商家店铺券
     */
    private Integer scopeType;

    /**
     * 商家ID，平台通用券为空；商家店铺券必须指定
     */
    private Long merchantId;

    /**
     * 领取范围: 0-公开领取 1-按分群领取 2-指定用户领取
     */
    private Integer audienceType;

    /**
     * 适用分群编码，多个值用逗号分隔
     */
    private String targetSegmentCodes;

    /**
     * 指定用户ID，多个值用逗号分隔
     */
    private String targetUserIds;

    /**
     * 领券说明 / 人群提示
     */
    private String audienceNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private Integer userCouponStatus;

    @TableField(exist = false)
    private Integer issuedCount;

    @TableField(exist = false)
    private Integer redeemedCount;

    @TableField(exist = false)
    private Integer paidOrderCount;

    /**
     * 核销率(%) = redeemedCount / issuedCount * 100
     */
    @TableField(exist = false)
    private BigDecimal redeemRate;

    /**
     * 支付转化率(%) = paidOrderCount / issuedCount * 100
     */
    @TableField(exist = false)
    private BigDecimal paidOrderRate;

    /**
     * 支付GMV
     */
    @TableField(exist = false)
    private BigDecimal paidGmv;

    /**
     * 平均支付客单价
     */
    @TableField(exist = false)
    private BigDecimal avgOrderAmount;
}
