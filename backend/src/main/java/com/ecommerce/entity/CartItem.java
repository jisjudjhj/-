package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cart_item")
public class CartItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private Integer selected;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String productName;

    @TableField(exist = false)
    private String productImage;

    @TableField(exist = false)
    private BigDecimal price;

    @TableField(exist = false)
    private Integer stock;

    @TableField(exist = false)
    private Integer productStatus;

    @TableField(exist = false)
    private Long merchantId;

    @TableField(exist = false)
    private Boolean checkoutBlocked;

    @TableField(exist = false)
    private String blockedReason;
}
