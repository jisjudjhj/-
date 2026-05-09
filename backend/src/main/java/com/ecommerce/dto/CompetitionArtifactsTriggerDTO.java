package com.ecommerce.dto;

public class CompetitionArtifactsTriggerDTO {

    private String snapshotDate;
    private String outputMode;
    private String ossPrefix;
    private String sparkMaster;
    private Integer shufflePartitions;
    private Integer behaviorWindowDays;
    private Integer orderWindowDays;
    private Boolean skipAiBrief;

    public String getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(String snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public String getOutputMode() {
        return outputMode;
    }

    public void setOutputMode(String outputMode) {
        this.outputMode = outputMode;
    }

    public String getOssPrefix() {
        return ossPrefix;
    }

    public void setOssPrefix(String ossPrefix) {
        this.ossPrefix = ossPrefix;
    }

    public String getSparkMaster() {
        return sparkMaster;
    }

    public void setSparkMaster(String sparkMaster) {
        this.sparkMaster = sparkMaster;
    }

    public Integer getShufflePartitions() {
        return shufflePartitions;
    }

    public void setShufflePartitions(Integer shufflePartitions) {
        this.shufflePartitions = shufflePartitions;
    }

    public Integer getBehaviorWindowDays() {
        return behaviorWindowDays;
    }

    public void setBehaviorWindowDays(Integer behaviorWindowDays) {
        this.behaviorWindowDays = behaviorWindowDays;
    }

    public Integer getOrderWindowDays() {
        return orderWindowDays;
    }

    public void setOrderWindowDays(Integer orderWindowDays) {
        this.orderWindowDays = orderWindowDays;
    }

    public Boolean getSkipAiBrief() {
        return skipAiBrief;
    }

    public void setSkipAiBrief(Boolean skipAiBrief) {
        this.skipAiBrief = skipAiBrief;
    }
}
