package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String orderNo;

    private BigDecimal totalAmount;

    private BigDecimal originalAmount;

    private BigDecimal discountAmount;

    private Long userCouponId;

    private Long seckillActivityId;

    private Long seckillApplyId;

    private Integer status;

    private String address;

    private String receiverName;

    private String receiverPhone;

    private String remark;

    private LocalDateTime payTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<OrderItem> items;

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private Boolean seckillOrder;

    public String getShippingAddress() {
        return address;
    }

    public void setShippingAddress(String shippingAddress) {
        this.address = shippingAddress;
    }
}
