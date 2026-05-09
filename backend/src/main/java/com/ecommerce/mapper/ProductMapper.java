package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    List<Product> selectHotProducts(@Param("limit") int limit);

    List<Product> selectRandomByCategory(@Param("categoryId") Long categoryId,
                                          @Param("limit") int limit);

    List<Map<String, Object>> selectAllCategoryIds();

    List<Product> selectByIds(@Param("ids") List<Long> ids);

    List<Map<String, Object>> selectCategorySalesStats();

    Map<String, Object> selectMerchantProductStats(@Param("merchantId") Long merchantId,
                                                   @Param("lowStockThreshold") int lowStockThreshold);

    List<Product> selectMerchantTopProducts(@Param("merchantId") Long merchantId, @Param("limit") int limit);

    List<Map<String, Object>> selectMerchantCategorySalesStats(@Param("merchantId") Long merchantId);

    int deductStock(@Param("id") Long id, @Param("quantity") int quantity);

    int restoreStock(@Param("id") Long id, @Param("quantity") int quantity);

    List<Map<String, Object>> selectSearchCategoryBuckets(@Param("categoryId") Long categoryId,
                                                          @Param("keywordParts") List<String> keywordParts,
                                                          @Param("minPrice") BigDecimal minPrice,
                                                          @Param("maxPrice") BigDecimal maxPrice);

    Map<String, Object> selectSearchPriceBucketSummary(@Param("categoryId") Long categoryId,
                                                       @Param("keywordParts") List<String> keywordParts,
                                                       @Param("minPrice") BigDecimal minPrice,
                                                       @Param("maxPrice") BigDecimal maxPrice);

    List<String> selectSearchCorrectionProductNames(@Param("limit") int limit);

    List<String> selectSearchCorrectionCategoryNames(@Param("limit") int limit);

    List<String> selectSearchCorrectionTagTerms(@Param("limit") int limit);
}
