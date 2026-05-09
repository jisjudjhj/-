package com.ecommerce.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;

@Data
public class MerchantProductBatchStockDTO {

    @NotEmpty(message = "商品ID列表不能为空")
    private List<Long> productIds;

    @NotBlank(message = "库存操作类型不能为空")
    @Pattern(regexp = "set|increase|decrease", message = "库存操作类型只能是 set、increase、decrease")
    private String operation;

    @NotNull(message = "库存值不能为空")
    @Min(value = 0, message = "库存值不能小于0")
    private Integer stock;
}
