package com.ecommerce.dto;

import lombok.Data;

@Data
public class SeckillAuditDTO {

    /**
     * true: 通过 false: 驳回
     */
    private Boolean approved;

    private String rejectReason;
}
