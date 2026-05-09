package com.ecommerce.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class SeckillArrangeDTO {

    @NotNull(message = "目标活动不能为空")
    private Long activityId;
}
