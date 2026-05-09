package com.ecommerce.recommendation;

import com.ecommerce.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationRerankServiceTest {

    private final RecommendationRerankService service = new RecommendationRerankService();

    @Test
    void clusterContextBoostsPreferredCategoryAndTags() {
        Product neutral = product(1L, "book", "paper", new BigDecimal("30"), 200, "paper");
        Product preferred = product(2L, "phone", "flagship", new BigDecimal("4999"), 10, "pro");
        Product other = product(3L, "snack", "food", new BigDecimal("20"), 1000, "sweet");

        RecommendationRerankService.RankingContext context =
                new RecommendationRerankService.RankingContext(
                        Collections.singletonList("phone"),
                        Collections.singletonList("pro"),
                        new BigDecimal("5000"));

        List<Product> ranked = service.rankByClusterContext(Arrays.asList(neutral, preferred, other), context, 3);

        assertEquals(2L, ranked.get(0).getId());
    }

    @Test
    void sceneGuardrailsLimitRepeatedMerchantAndNearDuplicates() {
        Product first = product(1L, "Laptop 2025 Pro", "computer", new BigDecimal("5000"), 50, "office");
        first.setMerchantId(10L);
        first.setImage("same-1.jpg");
        Product duplicate = product(2L, "Laptop 2026 Pro", "computer", new BigDecimal("5200"), 45, "office");
        duplicate.setMerchantId(10L);
        duplicate.setImage("same-2.jpg");
        Product different = product(3L, "Notebook", "book", new BigDecimal("20"), 30, "paper");
        different.setMerchantId(11L);

        List<Product> guarded = service.applySceneGuardrails(
                Arrays.asList(first, duplicate, different),
                2,
                new RecommendationRerankService.GuardrailConfig(1, 1, 1, 2, 2),
                Collections::emptyList);

        assertEquals(Arrays.asList(1L, 3L), Arrays.asList(guarded.get(0).getId(), guarded.get(1).getId()));
    }

    private Product product(Long id, String categoryName, String name, BigDecimal price, int sales, String tag) {
        Product product = new Product();
        product.setId(id);
        product.setCategoryName(categoryName);
        product.setName(name);
        product.setPrice(price);
        product.setSalesCount(sales);
        product.setRating(new BigDecimal("4.5"));
        product.setTags(Collections.singletonList(tag));
        return product;
    }
}
