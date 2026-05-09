package com.ecommerce.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class AnalyticsKmeansCouponCampaignDTO {

    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    @NotNull(message = "优惠券模板ID不能为空")
    private Long couponId;

    private Boolean sendNotification = true;
}
