package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.AnalyticsKmeansCouponCampaignDTO;
import com.ecommerce.dto.AnalyticsKmeansTriggerDTO;
import com.ecommerce.service.UserClusterAnalysisService;
import com.ecommerce.service.impl.KmeansLowActivityCouponCampaignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analysis/kmeans")
public class AdminKmeansAnalysisController {

    @Autowired
    private UserClusterAnalysisService userClusterAnalysisService;

    @Autowired
    private KmeansLowActivityCouponCampaignService campaignService;

    @GetMapping("/latest-task")
    public Result<?> latestTask() {
        return Result.success(userClusterAnalysisService.getLatestTask());
    }

    @GetMapping("/summary")
    public Result<?> summary() {
        return Result.success(userClusterAnalysisService.getLatestSummary());
    }

    @GetMapping("/segments")
    public Result<?> segments() {
        return Result.success(userClusterAnalysisService.getLatestSegments());
    }

    @GetMapping("/tasks")
    public Result<?> taskHistory(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        return Result.success(userClusterAnalysisService.getTaskHistory(page, size));
    }

    @PostMapping("/tasks/trigger")
    public Result<?> triggerTask(@Validated @RequestBody AnalyticsKmeansTriggerDTO request) {
        return Result.success(userClusterAnalysisService.triggerTask(request));
    }

    @PostMapping("/campaigns/low-activity-coupon")
    public Result<?> runLowActivityCouponCampaign(@Validated @RequestBody AnalyticsKmeansCouponCampaignDTO request) {
        return Result.success(campaignService.executeCampaign(
                request.getTaskId(),
                request.getCouponId(),
                request.getSendNotification() == null || request.getSendNotification(),
                "manual"
        ));
    }

    @GetMapping("/users")
    public Result<?> users(@RequestParam(required = false) String segmentCode,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int size) {
        return Result.success(userClusterAnalysisService.getSegmentUsers(segmentCode, page, size));
    }

    @GetMapping("/user/{userId}")
    public Result<?> userDetail(@PathVariable Long userId) {
        return Result.success(userClusterAnalysisService.getUserClusterDetail(userId));
    }
}
