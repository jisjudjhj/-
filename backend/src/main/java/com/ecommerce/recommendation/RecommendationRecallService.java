package com.ecommerce.recommendation;

import com.ecommerce.entity.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationRecallService {

    public List<Product> getPersonalRecommendationsLive(Long userId,
                                                        RecommendationRerankService.RankingContext rankingContext,
                                                        int limit,
                                                        RecallPort port) {
        List<Long> productIds = port.recommend(userId, limit);
        if (productIds.isEmpty()) {
            return port.getClusterAwareFallback(rankingContext, limit);
        }

        List<Product> products = port.mapAvailableProductsByIds(productIds, limit * 2);
        if (products.isEmpty()) {
            return port.getClusterAwareFallback(rankingContext, limit);
        }

        List<Product> rankedProducts = port.rankByClusterContext(products, rankingContext, limit * 2);
        rankedProducts = port.boostByRecentPurchaseCategories(userId, rankedProducts);
        return port.ensureCategoryDiversity(rankedProducts, limit);
    }

    public List<Product> getSimilarProductsLive(Long productId, int limit, RecallPort port) {
        List<Long> productIds = port.findSimilar(productId, limit);
        if (!productIds.isEmpty()) {
            List<Long> filteredIds = new ArrayList<>();
            for (Long id : productIds) {
                if (!Objects.equals(id, productId)) {
                    filteredIds.add(id);
                }
            }
            return port.mapAvailableProductsByIds(filteredIds, limit);
        }
        return port.selectSimilarByCategory(productId, limit);
    }

    public List<Product> getHotRecommendationsLive(Long userId,
                                                   RecommendationRerankService.RankingContext rankingContext,
                                                   int limit,
                                                   RecallPort port) {
        List<Product> realtimeHotProducts = port.loadRealtimeHotProducts(limit * 3);
        List<Product> baseProducts = realtimeHotProducts.isEmpty()
                ? port.getDiverseRecommendations(limit * 2)
                : appendFallbackProducts(
                realtimeHotProducts,
                port.getDiverseRecommendations(limit * 2),
                Math.max(limit * 3, realtimeHotProducts.size()));
        List<Product> rankedProducts = port.rankByClusterContext(
                baseProducts,
                rankingContext,
                Math.max(limit * 2, baseProducts.size()));
        rankedProducts = port.boostByRecentPurchaseCategories(userId, rankedProducts);
        return port.ensureCategoryDiversity(rankedProducts, Math.max(limit * 3, limit));
    }

    public List<Product> getGuessYouLikeLive(Long userId,
                                             RecommendationRerankService.RankingContext rankingContext,
                                             int limit,
                                             RecallPort port) {
        List<Long> productIds = port.recommend(userId, limit * 2);
        if (productIds.isEmpty()) {
            return port.getClusterAwareFallback(rankingContext, limit);
        }

        List<Product> products = port.mapAvailableProductsByIds(productIds, limit * 2);
        if (products.isEmpty()) {
            return port.getClusterAwareFallback(rankingContext, limit);
        }

        int candidateLimit = Math.max(limit * 3, limit);
        List<Product> rankedProducts = port.rankByClusterContext(
                products,
                rankingContext,
                Math.max(limit * 2, candidateLimit));
        rankedProducts = port.boostByRecentPurchaseCategories(userId, rankedProducts);
        List<Product> diverseResult = new ArrayList<>(port.ensureCategoryDiversity(rankedProducts, candidateLimit));
        if (diverseResult.size() >= candidateLimit) {
            return diverseResult;
        }

        Set<Long> existingIds = diverseResult.stream()
                .map(Product::getId)
                .collect(Collectors.toCollection(HashSet::new));
        List<Product> filler = port.getClusterAwareFallback(rankingContext, candidateLimit - diverseResult.size());
        for (Product product : filler) {
            if (product != null && existingIds.add(product.getId())) {
                diverseResult.add(product);
                if (diverseResult.size() >= candidateLimit) {
                    break;
                }
            }
        }
        return diverseResult;
    }

    private List<Product> appendFallbackProducts(List<Product> baseProducts, List<Product> fallbackProducts, int limit) {
        int safeLimit = Math.max(1, limit);
        List<Product> result = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        if (baseProducts != null) {
            for (Product product : baseProducts) {
                if (product == null || product.getId() == null) {
                    continue;
                }
                if (selectedIds.add(product.getId())) {
                    result.add(product);
                    if (result.size() >= safeLimit) {
                        return result;
                    }
                }
            }
        }
        if (fallbackProducts != null) {
            for (Product product : fallbackProducts) {
                if (product == null || product.getId() == null) {
                    continue;
                }
                if (selectedIds.add(product.getId())) {
                    result.add(product);
                    if (result.size() >= safeLimit) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    public interface RecallPort {
        List<Long> recommend(Long userId, int limit);

        List<Long> findSimilar(Long productId, int limit);

        List<Product> mapAvailableProductsByIds(List<Long> productIds, int limit);

        List<Product> getClusterAwareFallback(RecommendationRerankService.RankingContext rankingContext, int limit);

        List<Product> getDiverseRecommendations(int limit);

        List<Product> loadRealtimeHotProducts(int limit);

        List<Product> selectSimilarByCategory(Long productId, int limit);

        List<Product> rankByClusterContext(List<Product> products,
                                           RecommendationRerankService.RankingContext rankingContext,
                                           int limit);

        List<Product> boostByRecentPurchaseCategories(Long userId, List<Product> products);

        List<Product> ensureCategoryDiversity(List<Product> products, int limit);
    }
}
