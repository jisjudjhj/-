package com.ecommerce.dto;

import lombok.Data;

@Data
public class AnalyticsKmeansTriggerDTO {

    private String snapshotDate;

    private Integer k;

    private Boolean autoK;

    private Integer minK;

    private Integer maxK;
}
