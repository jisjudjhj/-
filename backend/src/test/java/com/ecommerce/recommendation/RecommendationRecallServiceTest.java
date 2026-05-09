package com.ecommerce.recommendation;

import com.ecommerce.entity.Product;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationRecallServiceTest {

    private final RecommendationRecallService service = new RecommendationRecallService();

    @Test
    void personalRecallFallsBackWhenHybridIdsAreEmpty() {
        FakePort port = new FakePort();
        port.fallbackProducts = Arrays.asList(product(8L), product(9L));

        List<Product> result = service.getPersonalRecommendationsLive(1L, null, 2, port);

        assertEquals(Arrays.asList(8L, 9L), ids(result));
    }

    @Test
    void guessYouLikeRecallAddsFallbackWhenRankedPoolIsShort() {
        FakePort port = new FakePort();
        port.recommendIds = Collections.singletonList(1L);
        port.availableProducts = Collections.singletonList(product(1L));
        port.fallbackProducts = Arrays.asList(product(2L), product(3L));

        List<Product> result = service.getGuessYouLikeLive(1L, null, 2, port);

        assertEquals(Arrays.asList(1L, 2L, 3L), ids(result));
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        return product;
    }

    private List<Long> ids(List<Product> products) {
        List<Long> ids = new ArrayList<>();
        for (Product product : products) {
            ids.add(product.getId());
        }
        return ids;
    }

    private static class FakePort implements RecommendationRecallService.RecallPort {
        private List<Long> recommendIds = Collections.emptyList();
        private List<Product> availableProducts = Collections.emptyList();
        private List<Product> fallbackProducts = Collections.emptyList();

        @Override
        public List<Long> recommend(Long userId, int limit) {
            return recommendIds;
        }

        @Override
        public List<Long> findSimilar(Long productId, int limit) {
            return Collections.emptyList();
        }

        @Override
        public List<Product> mapAvailableProductsByIds(List<Long> productIds, int limit) {
            return availableProducts;
        }

        @Override
        public List<Product> getClusterAwareFallback(RecommendationRerankService.RankingContext rankingContext, int limit) {
            return fallbackProducts;
        }

        @Override
        public List<Product> getDiverseRecommendations(int limit) {
            return fallbackProducts;
        }

        @Override
        public List<Product> loadRealtimeHotProducts(int limit) {
            return Collections.emptyList();
        }

        @Override
        public List<Product> selectSimilarByCategory(Long productId, int limit) {
            return fallbackProducts;
        }

        @Override
        public List<Product> rankByClusterContext(List<Product> products,
                                                  RecommendationRerankService.RankingContext rankingContext,
                                                  int limit) {
            return products;
        }

        @Override
        public List<Product> boostByRecentPurchaseCategories(Long userId, List<Product> products) {
            return products;
        }

        @Override
        public List<Product> ensureCategoryDiversity(List<Product> products, int limit) {
            return products;
        }
    }
}
