package com.ecommerce.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 应用启动后自动执行用户画像初始化，避免历史用户/批量导入用户缺失画像。
 */
@Component
public class RecommendationPreferenceBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecommendationPreferenceBootstrapRunner.class);

    @Autowired
    private UserPreferenceBootstrapService userPreferenceBootstrapService;

    @Value("${recommendation.preference-bootstrap.on-startup:true}")
    private boolean bootstrapOnStartup;

    @Value("${recommendation.preference-bootstrap.startup-force:false}")
    private boolean startupForceRebuild;

    @Value("${recommendation.preference-bootstrap.startup-limit:0}")
    private int startupLimit;

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapOnStartup) {
            return;
        }
        try {
            Map<String, Object> summary = userPreferenceBootstrapService.bootstrapAllUsers(startupForceRebuild, startupLimit);
            log.info("[PreferenceBootstrap] startup summary={}", summary);
        } catch (Exception exception) {
            log.warn("[PreferenceBootstrap] startup bootstrap failed: {}", exception.getMessage());
        }
    }
}
