package com.ecommerce.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
public class OrderCreateDTO {

    private Long addressId;

    private String address;

    private String receiverName;

    private String receiverPhone;

    private String remark;

    private Long userCouponId;

    private Map<Long, Long> splitCoupons;

    @NotEmpty(message = "购买商品不能为空")
    @Valid
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO {
        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量至少为1")
        private Integer quantity;
    }
}
