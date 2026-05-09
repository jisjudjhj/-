package com.ecommerce.dto;

import lombok.Data;

import javax.validation.constraints.Min;

@Data
public class SeckillCheckoutPreviewDTO {

    private Long applyId;

    private Long seckillApplyId;

    private Long productId;

    @Min(value = 1, message = "购买数量必须大于0")
    private Integer quantity;

    public Long resolveApplyId() {
        return seckillApplyId != null ? seckillApplyId : applyId;
    }
}
