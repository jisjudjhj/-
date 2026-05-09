package com.ecommerce.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.SeckillActivityApply;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.SeckillActivityApplyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SeckillService {

    public static final String CART_BLOCK_REASON = "秒杀商品请直接使用秒杀购买";

    @Autowired
    private SeckillActivityApplyMapper seckillActivityApplyMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrderMapper orderMapper;

    public SeckillActivityApply getActiveApplyByProductId(Long productId) {
        if (productId == null) {
            return null;
        }
        return seckillActivityApplyMapper.selectActiveByProductId(productId, LocalDateTime.now());
    }

    public SeckillActivityApply getUpcomingOrActiveApplyByProductId(Long productId) {
        if (productId == null) {
            return null;
        }
        return seckillActivityApplyMapper.selectUpcomingOrActiveByProductId(productId, LocalDateTime.now());
    }

    public boolean isActiveSeckillProduct(Long productId) {
        return getActiveApplyByProductId(productId) != null;
    }

    public boolean isShelfSeckillProduct(Long productId) {
        return getUpcomingOrActiveApplyByProductId(productId) != null;
    }

    public List<Product> excludeShelfSeckillProducts(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return new ArrayList<>();
        }
        List<Product> filtered = new ArrayList<>(products.size());
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                continue;
            }
            if (!isShelfSeckillProduct(product.getId())) {
                filtered.add(product);
            }
        }
        return filtered;
    }

    public Long resolveActiveApplyIdByProductId(Long productId) {
        SeckillActivityApply apply = getActiveApplyByProductId(productId);
        return apply == null ? null : apply.getId();
    }

    public void fillProductSeckillInfo(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        SeckillActivityApply apply = getUpcomingOrActiveApplyByProductId(product.getId());
        if (apply == null) {
            clearProductSeckillInfo(product);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int runtimeStatus = now.isBefore(apply.getActivityStartTime())
                ? Constants.SeckillRuntimeStatus.UPCOMING
                : (now.isAfter(apply.getActivityEndTime())
                ? Constants.SeckillRuntimeStatus.ENDED
                : Constants.SeckillRuntimeStatus.ACTIVE);
        int remainingStock = Math.max(0, safeInt(apply.getSeckillStock()) - safeInt(apply.getSoldCount()));
        int seckillStatus = runtimeStatus == Constants.SeckillRuntimeStatus.ACTIVE && remainingStock <= 0
                ? Constants.SeckillRuntimeStatus.SOLD_OUT
                : runtimeStatus;
        product.setSeckillActivityId(apply.getActivityId());
        product.setSeckillApplyId(apply.getId());
        product.setSeckillPrice(apply.getSeckillPrice());
        product.setSeckillStartTime(apply.getActivityStartTime());
        product.setSeckillEndTime(apply.getActivityEndTime());
        product.setSeckillStock(remainingStock);
        product.setSeckillLimitPerUser(safeInt(apply.getLimitPerUser()) <= 0 ? 1 : apply.getLimitPerUser());
        product.setSeckillStatus(seckillStatus);
    }

    public void fillProductSeckillInfo(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        for (Product product : products) {
            fillProductSeckillInfo(product);
        }
    }

    public List<Map<String, Object>> getActiveSeckillProductCards(int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        List<Map<String, Object>> rows = seckillActivityApplyMapper.selectActiveProducts(LocalDateTime.now(), safeLimit);
        return buildProductCards(rows);
    }

    public List<Map<String, Object>> getDisplaySeckillProductCards(int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        List<Map<String, Object>> rows = seckillActivityApplyMapper.selectDisplayProducts(LocalDateTime.now(), safeLimit);
        return buildProductCards(rows);
    }

    public List<Map<String, Object>> getDisplaySeckillProductCards(int limit, boolean includeHistory) {
        return includeHistory ? getHallSeckillProductCards(limit) : getDisplaySeckillProductCards(limit);
    }

    public List<Map<String, Object>> getHallSeckillProductCards(int limit) {
        int safeLimit = limit <= 0 ? 30 : Math.min(limit, 200);
        List<Map<String, Object>> rows = seckillActivityApplyMapper.selectHallDisplayProducts(LocalDateTime.now(), safeLimit);
        return buildProductCards(rows);
    }

    public List<Map<String, Object>> getDisplaySeckillActivityGroups(int limit, boolean includeHistory) {
        List<Map<String, Object>> cards = getDisplaySeckillProductCards(limit, includeHistory);
        return buildActivityGroups(cards);
    }

    private List<Map<String, Object>> buildProductCards(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> cards = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            int runtimeStatus = safeInt(row.get("runtimeStatus"));
            int remainingStock = Math.max(0, safeInt(row.get("seckillStock")) - safeInt(row.get("soldCount")));
            int seckillStatus = runtimeStatus == Constants.SeckillRuntimeStatus.ACTIVE && remainingStock <= 0
                    ? Constants.SeckillRuntimeStatus.SOLD_OUT
                    : runtimeStatus;
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("productId", row.get("productId"));
            card.put("productName", row.get("productName"));
            card.put("productImage", row.get("productImage"));
            card.put("productPrice", row.get("productPrice"));
            card.put("salesCount", row.get("salesCount"));
            card.put("rating", row.get("rating"));
            card.put("seckillApplyId", row.get("applyId"));
            card.put("seckillActivityId", row.get("activityId"));
            card.put("seckillPrice", row.get("seckillPrice"));
            card.put("seckillStock", row.get("seckillStock"));
            card.put("soldCount", row.get("soldCount"));
            card.put("remainingStock", Math.max(0, safeInt(row.get("seckillStock")) - safeInt(row.get("soldCount"))));
            card.put("limitPerUser", row.get("limitPerUser"));
            card.put("activityName", row.get("activityName"));
            card.put("activityCoverImage", row.get("activityCoverImage"));
            card.put("activityDescription", row.get("activityDescription"));
            card.put("startTime", row.get("startTime"));
            card.put("endTime", row.get("endTime"));
            card.put("runtimeStatus", runtimeStatus);
            card.put("seckillStatus", seckillStatus);
            cards.add(card);
        }
        return cards;
    }

    private List<Map<String, Object>> buildActivityGroups(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> card : cards) {
            Long activityId = safeLong(card.get("seckillActivityId"));
            String startTime = Objects.toString(card.get("startTime"), "");
            String endTime = Objects.toString(card.get("endTime"), "");
            String groupKey = activityId + "_" + startTime + "_" + endTime;
            Map<String, Object> group = grouped.get(groupKey);
            if (group == null) {
                group = new LinkedHashMap<>();
                group.put("activityId", activityId);
                group.put("activityName", card.get("activityName"));
                group.put("activityCoverImage", card.get("activityCoverImage"));
                group.put("activityDescription", card.get("activityDescription"));
                group.put("startTime", card.get("startTime"));
                group.put("endTime", card.get("endTime"));
                group.put("runtimeStatus", card.get("runtimeStatus"));
                group.put("seckillStatus", card.get("seckillStatus"));
                group.put("productCount", 0);
                group.put("remainingStock", 0);
                group.put("items", new ArrayList<Map<String, Object>>());
                grouped.put(groupKey, group);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) group.get("items");
            items.add(card);
            group.put("productCount", safeInt(group.get("productCount")) + 1);
            group.put("remainingStock", safeInt(group.get("remainingStock")) + safeInt(card.get("remainingStock")));
        }
        return new ArrayList<>(grouped.values());
    }

    public SeckillActivityApply requireOrderableApply(Long applyId, Long userId, Integer quantity) {
        if (applyId == null || applyId <= 0) {
            throw new BusinessException("秒杀报名不存在");
        }
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("购买数量必须大于0");
        }
        SeckillActivityApply apply = seckillActivityApplyMapper.selectWithActivity(applyId);
        if (apply == null) {
            throw new BusinessException("秒杀报名不存在");
        }
        if (!Objects.equals(apply.getAuditStatus(), Constants.SeckillAuditStatus.APPROVED)) {
            throw new BusinessException("该秒杀报名尚未通过审核");
        }
        if (!Objects.equals(apply.getPublishStatus(), Constants.SeckillPublishStatus.PUBLISHED)) {
            throw new BusinessException("该秒杀活动未发布");
        }
        LocalDateTime now = LocalDateTime.now();
        if (apply.getActivityStartTime() == null || apply.getActivityEndTime() == null
                || now.isBefore(apply.getActivityStartTime()) || now.isAfter(apply.getActivityEndTime())) {
            throw new BusinessException("当前不在秒杀活动时间范围内");
        }
        int totalStock = safeInt(apply.getSeckillStock());
        int soldCount = safeInt(apply.getSoldCount());
        if (totalStock - soldCount < quantity) {
            throw new BusinessException("秒杀库存不足");
        }
        Product product = productMapper.selectById(apply.getProductId());
        if (product == null || !Objects.equals(product.getStatus(), Constants.ProductStatus.ON_SHELF)) {
            throw new BusinessException("商品不存在或已下架");
        }
        if (product.getStock() == null || product.getStock() < quantity) {
            throw new BusinessException("商品库存不足");
        }
        int limitPerUser = safeInt(apply.getLimitPerUser()) <= 0 ? 1 : apply.getLimitPerUser();
        Integer purchasedQty = orderMapper.selectUserSeckillPurchasedQuantity(userId, applyId);
        int safePurchasedQty = purchasedQty == null ? 0 : purchasedQty;
        if (safePurchasedQty + quantity > limitPerUser) {
            throw new BusinessException("超过每人限购数量");
        }
        if (apply.getSeckillPrice() == null || apply.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("秒杀价格配置异常");
        }
        if (apply.getSeckillPrice().compareTo(product.getPrice()) >= 0) {
            throw new BusinessException("秒杀价格配置异常");
        }
        apply.setProduct(product);
        return apply;
    }

    public Map<String, Object> buildCheckoutPreview(Long userId, Long applyId, Integer quantity) {
        SeckillActivityApply apply = requireOrderableApply(applyId, userId, quantity);
        Product product = apply.getProduct();

        BigDecimal originalAmount = product.getPrice().multiply(new BigDecimal(quantity));
        BigDecimal totalAmount = apply.getSeckillPrice().multiply(new BigDecimal(quantity));
        BigDecimal discountAmount = originalAmount.subtract(totalAmount);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", product.getId());
        item.put("productName", product.getName());
        item.put("productImage", product.getImage());
        item.put("price", apply.getSeckillPrice());
        item.put("originalPrice", product.getPrice());
        item.put("quantity", quantity);
        item.put("subtotal", totalAmount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "seckill");
        result.put("seckillActivityId", apply.getActivityId());
        result.put("seckillApplyId", apply.getId());
        result.put("activityName", apply.getActivityName());
        result.put("startTime", apply.getActivityStartTime());
        result.put("endTime", apply.getActivityEndTime());
        result.put("limitPerUser", apply.getLimitPerUser());
        result.put("seckillPrice", apply.getSeckillPrice());
        result.put("item", item);
        result.put("itemCount", 1);
        result.put("totalAmount", totalAmount);
        result.put("originalAmount", originalAmount);
        result.put("discountAmount", discountAmount);
        result.put("finalAmount", totalAmount);
        result.put("shippingFee", BigDecimal.ZERO);
        result.put("couponDisabled", true);

        // 兼容小程序旧结构（扁平字段）
        result.put("productId", product.getId());
        result.put("productName", product.getName());
        result.put("productImage", product.getImage());
        result.put("price", apply.getSeckillPrice());
        result.put("originalPrice", product.getPrice());
        result.put("quantity", quantity);
        result.put("subtotal", totalAmount);
        result.put("seckillStartTime", apply.getActivityStartTime());
        result.put("seckillEndTime", apply.getActivityEndTime());
        result.put("seckillLimitPerUser", apply.getLimitPerUser());
        return result;
    }

    private void clearProductSeckillInfo(Product product) {
        product.setSeckillActivityId(null);
        product.setSeckillApplyId(null);
        product.setSeckillPrice(null);
        product.setSeckillStartTime(null);
        product.setSeckillEndTime(null);
        product.setSeckillStock(null);
        product.setSeckillLimitPerUser(null);
        product.setSeckillStatus(null);
    }

    private int safeInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Long safeLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
