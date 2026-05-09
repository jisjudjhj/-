package com.ecommerce.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTransactionFlowContractTest {

    @Test
    void orderServiceKeepsCriticalTransactionGuards() throws Exception {
        String source = read("src/main/java/com/ecommerce/service/impl/OrderServiceImpl.java");

        assertTrue(source.contains("public List<Order> createOrders"), "下单入口必须支持跨店拆单");
        assertTrue(source.contains("merchantGroupedItems"), "跨店拆单必须按商家归组订单明细");
        assertTrue(source.contains("商品库存不足") || source.contains("秒杀库存不足"), "下单必须保留库存不足拦截");
        assertTrue(source.contains("订单已支付，请勿重复操作"), "余额支付必须保留重复支付拦截");
        assertTrue(source.contains("deductBalance"), "余额支付必须扣减钱包余额");
        assertTrue(source.contains("优惠券已过期"), "下单必须校验优惠券有效期");
        assertTrue(source.contains("优惠券已被其他订单占用"), "下单必须校验优惠券并发占用");
    }

    @Test
    void refundFlowKeepsRecommendationAttributionEmitter() throws Exception {
        String source = read("src/main/java/com/ecommerce/controller/RefundController.java");

        assertTrue(source.contains("emitRefundRecommendationEvents"), "退款成功必须保留推荐归因事件发射入口");
        assertTrue(source.contains("Constants.RecommendationEventType.REFUND"), "退款归因必须使用 refund 推荐事件");
        assertTrue(source.contains("recordRecommendationEventAsync"), "退款归因必须进入异步推荐事件链路");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
