package com.ecommerce.recommendation;

import com.ecommerce.entity.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

@Service
public class RecommendationGovernanceService {

    @Value("${recommendation.governance.strategy-version:hybrid-enterprise-v2}")
    private String strategyVersion;

    @Value("${recommendation.governance.gray-enabled:true}")
    private boolean grayEnabled;

    @Value("${recommendation.governance.gray-ratio:100}")
    private int grayRatio;

    @Value("${recommendation.governance.fallback-to-hot-when-outside-gray:true}")
    private boolean fallbackToHotWhenOutsideGray;

    public boolean shouldUseEnterpriseStrategy(Long userId, String scene) {
        if (!grayEnabled) {
            return false;
        }
        int safeRatio = Math.max(0, Math.min(100, grayRatio));
        if (safeRatio >= 100) {
            return true;
        }
        if (safeRatio <= 0) {
            return false;
        }
        String seed = (userId == null ? "anonymous" : String.valueOf(userId)) + ":" + (scene == null ? "" : scene);
        CRC32 crc32 = new CRC32();
        byte[] bytes = seed.getBytes(StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
        return (crc32.getValue() % 100) < safeRatio;
    }

    public boolean shouldFallbackToHotWhenOutsideGray() {
        return fallbackToHotWhenOutsideGray;
    }

    public String getStrategyVersion() {
        return StringUtils.hasText(strategyVersion) ? strategyVersion : "hybrid-enterprise-v2";
    }

    public void stampProducts(List<Product> products, String scene) {
        if (products == null || products.isEmpty()) {
            return;
        }
        String version = getStrategyVersion();
        for (Product product : products) {
            if (product == null) {
                continue;
            }
            product.setModelVersion(version);
            if (!StringUtils.hasText(product.getRecommendationScene())) {
                product.setRecommendationScene(scene);
            }
            if (!StringUtils.hasText(product.getDataFreshness())) {
                product.setDataFreshness("request-time");
            }
        }
    }
}
