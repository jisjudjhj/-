package com.ecommerce.scheduler;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ModuleDisabledException;
import com.ecommerce.config.AnalyticsKmeansCampaignProperties;
import com.ecommerce.service.impl.KmeansLowActivityCouponCampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KmeansLowActivityCouponCampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(KmeansLowActivityCouponCampaignScheduler.class);

    private final AnalyticsKmeansCampaignProperties campaignProperties;
    private final KmeansLowActivityCouponCampaignService campaignService;

    public KmeansLowActivityCouponCampaignScheduler(AnalyticsKmeansCampaignProperties campaignProperties,
                                                    KmeansLowActivityCouponCampaignService campaignService) {
        this.campaignProperties = campaignProperties;
        this.campaignService = campaignService;
    }

    @Scheduled(fixedDelayString = "${analytics.kmeans.campaign.scheduler-fixed-delay-ms:60000}")
    public void processPendingCampaigns() {
        if (!campaignProperties.isEnabled()
                || !campaignProperties.isAutoProcessEnabled()
                || campaignProperties.getDefaultCouponId() == null
                || campaignProperties.getDefaultCouponId() <= 0) {
            return;
        }

        List<Long> taskIds = campaignService.listPendingAutoTaskIds();
        if (taskIds.isEmpty()) {
            return;
        }

        for (Long taskId : taskIds) {
            try {
                campaignService.executeCampaign(taskId, campaignProperties.getDefaultCouponId(), true, "scheduler");
            } catch (ModuleDisabledException ex) {
                log.info("Skip auto low-activity coupon campaign because module is disabled: taskId={}, couponId={}, message={}",
                        taskId, campaignProperties.getDefaultCouponId(), ex.getMessage());
                break;
            } catch (BusinessException ex) {
                if (shouldMarkAsPermanentFailure(ex)) {
                    campaignService.recordCampaignFailure(
                            taskId,
                            campaignProperties.getDefaultCouponId(),
                            "scheduler",
                            "business_" + ex.getCode(),
                            ex.getMessage()
                    );
                    log.warn("Auto low-activity coupon campaign marked as failed: taskId={}, couponId={}, code={}, message={}",
                            taskId, campaignProperties.getDefaultCouponId(), ex.getCode(), ex.getMessage());
                } else {
                    log.warn("Auto low-activity coupon campaign failed temporarily: taskId={}, couponId={}, code={}, message={}",
                            taskId, campaignProperties.getDefaultCouponId(), ex.getCode(), ex.getMessage());
                }
            } catch (Exception ex) {
                log.warn("Auto low-activity coupon campaign failed temporarily: taskId={}, couponId={}, message={}",
                        taskId, campaignProperties.getDefaultCouponId(), ex.getMessage(), ex);
            }
        }
    }

    private boolean shouldMarkAsPermanentFailure(BusinessException ex) {
        return ex != null && (ex.getCode() == 400 || ex.getCode() == 404);
    }
}
