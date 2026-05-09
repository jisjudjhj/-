package com.ecommerce.service;

import com.ecommerce.dto.AnalyticsKmeansTriggerDTO;

import java.util.Map;

public interface UserClusterAnalysisService {

    Map<String, Object> getLatestTask();

    Map<String, Object> getLatestSummary();

    Map<String, Object> getLatestSegments();

    Map<String, Object> getSegmentUsers(String segmentCode, int page, int size);

    Map<String, Object> getUserClusterDetail(Long userId);

    Map<String, Object> getTaskHistory(int page, int size);

    Map<String, Object> triggerTask(AnalyticsKmeansTriggerDTO request);
}
