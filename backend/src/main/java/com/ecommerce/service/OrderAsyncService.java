package com.ecommerce.service;

import com.ecommerce.service.impl.PurchaseAttributionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderAsyncService {

    private static final Logger log = LoggerFactory.getLogger(OrderAsyncService.class);

    private final PurchaseAttributionService purchaseAttributionService;

    public OrderAsyncService(PurchaseAttributionService purchaseAttributionService) {
        this.purchaseAttributionService = purchaseAttributionService;
    }

    @Async("orderTaskExecutor")
    public void recordPurchaseBehavior(Long orderId, Long userId) {
        try {
            purchaseAttributionService.recordPurchaseBehavior(orderId, userId);
        } catch (Exception ex) {
            log.warn("[OrderAsync] recordPurchaseBehavior failed, orderId={}, userId={}, error={}",
                    orderId, userId, ex.getMessage());
        }
    }
}
