package com.ecommerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductReview;
import com.ecommerce.entity.ProfileChangeRequest;
import com.ecommerce.entity.RefundRequest;
import com.ecommerce.entity.SeckillActivityApply;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.ProductReviewMapper;
import com.ecommerce.mapper.ProfileChangeRequestMapper;
import com.ecommerce.mapper.RefundMapper;
import com.ecommerce.mapper.SeckillActivityApplyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ManagementWorkbenchBadgeService {

    public static final String SCOPE_PROFILE_CHANGE = "profile-change";
    public static final String SCOPE_REFUND = "refund";
    public static final String SCOPE_REVIEW = "review";
    public static final String SCOPE_SECKILL = "seckill";
    public static final String SCOPE_MESSAGE = "message";

    private static final List<String> ADMIN_DEFAULT_SCOPES = Arrays.asList(
            SCOPE_PROFILE_CHANGE, SCOPE_REFUND, SCOPE_REVIEW, SCOPE_SECKILL);
    private static final List<String> MERCHANT_DEFAULT_SCOPES = Arrays.asList(
            SCOPE_MESSAGE, SCOPE_REFUND, SCOPE_REVIEW, SCOPE_SECKILL);

    @Autowired
    private ProfileChangeRequestMapper profileChangeRequestMapper;

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private ProductReviewMapper productReviewMapper;

    @Autowired
    private SeckillActivityApplyMapper seckillActivityApplyMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    public Map<String, Object> getAdminBadgeCounts(Collection<String> scopes) {
        Set<String> normalizedScopes = normalizeScopes(scopes, true);
        Map<String, Object> counts = new LinkedHashMap<>();
        if (normalizedScopes.contains(SCOPE_PROFILE_CHANGE)) {
            counts.put("profileChanges", countAdminProfileChanges());
        }
        if (normalizedScopes.contains(SCOPE_REFUND)) {
            counts.put("refunds", countAdminPendingRefunds());
        }
        if (normalizedScopes.contains(SCOPE_REVIEW)) {
            counts.put("reviews", countAdminPendingReviews());
        }
        if (normalizedScopes.contains(SCOPE_SECKILL)) {
            counts.put("seckillApplications", countAdminPendingSeckillApplications());
        }
        return counts;
    }

    public Map<String, Object> getMerchantBadgeCounts(Long merchantId, Collection<String> scopes) {
        Set<String> normalizedScopes = normalizeScopes(scopes, false);
        Map<String, Object> counts = new LinkedHashMap<>();
        if (merchantId == null || merchantId <= 0) {
            if (normalizedScopes.contains(SCOPE_MESSAGE)) {
                counts.put("messages", 0L);
            }
            if (normalizedScopes.contains(SCOPE_REFUND)) {
                counts.put("refunds", 0L);
            }
            if (normalizedScopes.contains(SCOPE_REVIEW)) {
                counts.put("reviews", 0L);
            }
            if (normalizedScopes.contains(SCOPE_SECKILL)) {
                counts.put("seckillApplications", 0L);
            }
            return counts;
        }

        Set<Long> merchantProductIds = null;
        if (normalizedScopes.contains(SCOPE_REFUND) || normalizedScopes.contains(SCOPE_REVIEW)) {
            merchantProductIds = loadMerchantProductIds(merchantId);
        }

        if (normalizedScopes.contains(SCOPE_MESSAGE)) {
            counts.put("messages", (long) Math.max(messageMapper.selectUnreadCount(merchantId), 0));
        }
        if (normalizedScopes.contains(SCOPE_REFUND)) {
            counts.put("refunds", countMerchantPendingRefunds(merchantProductIds));
        }
        if (normalizedScopes.contains(SCOPE_REVIEW)) {
            counts.put("reviews", countMerchantPendingReviews(merchantProductIds));
        }
        if (normalizedScopes.contains(SCOPE_SECKILL)) {
            counts.put("seckillApplications", countMerchantPendingSeckillApplications(merchantId));
        }
        return counts;
    }

    private long countAdminProfileChanges() {
        return profileChangeRequestMapper.selectCount(
                new LambdaQueryWrapper<ProfileChangeRequest>()
                        .eq(ProfileChangeRequest::getStatus, 0)
        );
    }

    private long countAdminPendingRefunds() {
        return refundMapper.selectCount(
                new LambdaQueryWrapper<RefundRequest>()
                        .eq(RefundRequest::getStatus, Constants.RefundStatus.PENDING)
        );
    }

    private long countAdminPendingReviews() {
        return productReviewMapper.selectCount(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getStatus, Constants.ReviewStatus.PENDING)
        );
    }

    private long countAdminPendingSeckillApplications() {
        return seckillActivityApplyMapper.selectCount(
                new LambdaQueryWrapper<SeckillActivityApply>()
                        .eq(SeckillActivityApply::getAuditStatus, Constants.SeckillAuditStatus.PENDING)
        );
    }

    private long countMerchantPendingSeckillApplications(Long merchantId) {
        return seckillActivityApplyMapper.selectCount(
                new LambdaQueryWrapper<SeckillActivityApply>()
                        .eq(SeckillActivityApply::getMerchantId, merchantId)
                        .eq(SeckillActivityApply::getAuditStatus, Constants.SeckillAuditStatus.PENDING)
        );
    }

    private long countMerchantPendingReviews(Set<Long> merchantProductIds) {
        if (merchantProductIds == null || merchantProductIds.isEmpty()) {
            return 0L;
        }
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .in(ProductReview::getProductId, merchantProductIds)
                .and(w -> w.isNull(ProductReview::getReply).or().eq(ProductReview::getReply, ""));
        return productReviewMapper.selectCount(wrapper);
    }

    private long countMerchantPendingRefunds(Set<Long> merchantProductIds) {
        if (merchantProductIds == null || merchantProductIds.isEmpty()) {
            return 0L;
        }
        Set<Long> merchantOrderIds = loadMerchantOrderIds(merchantProductIds);
        if (merchantOrderIds.isEmpty()) {
            return 0L;
        }
        Set<Long> exclusiveOrderIds = filterExclusiveMerchantOrderIds(merchantProductIds, merchantOrderIds);
        if (exclusiveOrderIds.isEmpty()) {
            return 0L;
        }
        return refundMapper.selectCount(
                new LambdaQueryWrapper<RefundRequest>()
                        .in(RefundRequest::getOrderId, exclusiveOrderIds)
                        .eq(RefundRequest::getStatus, Constants.RefundStatus.PENDING)
        );
    }

    private Set<Long> loadMerchantProductIds(Long merchantId) {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchantId)
                        .select(Product::getId)
        );
        if (products == null || products.isEmpty()) {
            return Collections.emptySet();
        }
        return products.stream()
                .map(Product::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> loadMerchantOrderIds(Set<Long> merchantProductIds) {
        if (merchantProductIds == null || merchantProductIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<OrderItem> merchantItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getProductId, merchantProductIds)
                        .select(OrderItem::getOrderId, OrderItem::getProductId)
        );
        if (merchantItems == null || merchantItems.isEmpty()) {
            return Collections.emptySet();
        }
        return merchantItems.stream()
                .map(OrderItem::getOrderId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> filterExclusiveMerchantOrderIds(Set<Long> merchantProductIds, Set<Long> candidateOrderIds) {
        if (merchantProductIds == null || merchantProductIds.isEmpty()
                || candidateOrderIds == null || candidateOrderIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<OrderItem> allOrderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, candidateOrderIds)
                        .select(OrderItem::getOrderId, OrderItem::getProductId)
        );
        if (allOrderItems == null || allOrderItems.isEmpty()) {
            return Collections.emptySet();
        }

        Map<Long, List<OrderItem>> grouped = allOrderItems.stream()
                .filter(item -> item.getOrderId() != null)
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Set<Long> exclusiveIds = new LinkedHashSet<>();
        for (Map.Entry<Long, List<OrderItem>> entry : grouped.entrySet()) {
            List<OrderItem> orderItems = entry.getValue();
            if (orderItems == null || orderItems.isEmpty()) {
                continue;
            }
            boolean ownedAll = true;
            for (OrderItem orderItem : orderItems) {
                if (orderItem.getProductId() == null || !merchantProductIds.contains(orderItem.getProductId())) {
                    ownedAll = false;
                    break;
                }
            }
            if (ownedAll) {
                exclusiveIds.add(entry.getKey());
            }
        }
        return exclusiveIds;
    }

    private Set<String> normalizeScopes(Collection<String> scopes, boolean admin) {
        List<String> defaults = admin ? ADMIN_DEFAULT_SCOPES : MERCHANT_DEFAULT_SCOPES;
        if (scopes == null || scopes.isEmpty()) {
            return new LinkedHashSet<>(defaults);
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            String value = normalizeScope(scope);
            if (value != null) {
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            normalized.addAll(defaults);
        } else {
            normalized.retainAll(new LinkedHashSet<>(defaults));
        }
        return normalized;
    }

    public List<String> parseScopes(String rawScopes) {
        if (!StringUtils.hasText(rawScopes)) {
            return new ArrayList<>();
        }
        String[] parts = rawScopes.split("[,，\\s]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String normalized = normalizeScope(part);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalizeScope(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        if (SCOPE_PROFILE_CHANGE.equals(value) || "profile".equals(value) || "profilechange".equals(value)) {
            return SCOPE_PROFILE_CHANGE;
        }
        if (SCOPE_REFUND.equals(value) || "refunds".equals(value)) {
            return SCOPE_REFUND;
        }
        if (SCOPE_REVIEW.equals(value) || "reviews".equals(value)) {
            return SCOPE_REVIEW;
        }
        if (SCOPE_SECKILL.equals(value) || "seckill-application".equals(value) || "seckill-apply".equals(value)) {
            return SCOPE_SECKILL;
        }
        if (SCOPE_MESSAGE.equals(value) || "messages".equals(value)) {
            return SCOPE_MESSAGE;
        }
        return null;
    }
}
