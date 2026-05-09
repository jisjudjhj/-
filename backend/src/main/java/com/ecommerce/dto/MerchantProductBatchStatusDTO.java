package com.ecommerce.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class MerchantProductBatchStatusDTO {

    @NotEmpty(message = "商品ID列表不能为空")
    private List<Long> productIds;

    @NotNull(message = "商品状态不能为空")
    @Min(value = 0, message = "商品状态不合法")
    @Max(value = 1, message = "商品状态不合法")
    private Integer status;
}
