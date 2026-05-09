package com.ecommerce.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.Product;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProductService extends IService<Product> {

    IPage<Product> getProductPage(int page, int size, Long categoryId, String keyword,
                                   BigDecimal minPrice, BigDecimal maxPrice, String sortField, String sortOrder);

    Map<String, Object> getSearchFacets(Long categoryId, String keyword,
                                        BigDecimal minPrice, BigDecimal maxPrice);

    Product getProductDetail(Long id);

    List<Product> getHotProducts(int limit);

    List<Product> getProductsByIds(List<Long> ids);

    IPage<Product> getMerchantProducts(Long merchantId, int page, int size);

    IPage<Product> getMerchantProducts(Long merchantId, int page, int size, String keyword,
                                       Integer status, Long categoryId, String stockStatus,
                                       String sortField, String sortOrder);

    int batchUpdateMerchantProductStatus(Long merchantId, List<Long> productIds, Integer status);

    int batchUpdateMerchantProductStock(Long merchantId, List<Long> productIds, String operation, Integer stock);

    IPage<Product> getMerchantLowStockProducts(Long merchantId, int threshold, int page, int size);

    Map<String, Object> getMerchantProductStats(Long merchantId, int lowStockThreshold, int topLimit);

    List<Map<String, Object>> getCategorySalesStats();

    void evictProductCaches(Collection<Long> productIds);
}
