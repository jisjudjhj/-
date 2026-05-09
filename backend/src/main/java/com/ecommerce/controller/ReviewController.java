package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Result;
import com.ecommerce.entity.*;
import com.ecommerce.mapper.*;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.RiskControlService;
import com.ecommerce.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int HELPFUL_USER_WINDOW_SECONDS = 60;
    private static final int HELPFUL_USER_MAX_REQUESTS = 24;
    private static final int HELPFUL_DEVICE_WINDOW_SECONDS = 60;
    private static final int HELPFUL_DEVICE_MAX_REQUESTS = 45;
    private static final int HELPFUL_DEVICE_UNIQUE_USER_WINDOW_SECONDS = 600;
    private static final int HELPFUL_DEVICE_MAX_UNIQUE_USERS = 10;
    private static final int HELPFUL_DEVICE_HOURLY_LIMIT = 180;
    private static final int LOW_RATING_THRESHOLD = 2;
    private static final int LOW_RATING_PENDING_TIMEOUT_MINUTES = 10;
    private static final String HELPFUL_RATE_KEY_PREFIX = "review:helpful:rate:";
    private static final String HELPFUL_DEVICE_USER_SET_PREFIX = "review:helpful:device:users:";
    private static final String HELPFUL_HOURLY_KEY_PREFIX = "review:helpful:device:hour:";

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private ProductReviewMapper reviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductReviewVoteMapper reviewVoteMapper;

    @Autowired
    private ImConversationMapper imConversationMapper;

    @Autowired
    private ImTicketMapper imTicketMapper;

    @Autowired
    private ImMessageMapper imMessageMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RiskControlService riskControlService;

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @GetMapping("/product/{productId}")
    public Result<?> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean hasVideo,
            @RequestParam(required = false) String tag,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("review");
        if (!moduleSwitchService.isEnabled("review")) {
            Map<String, Object> result = new HashMap<>();
            result.put("reviews", emptyPage(page, size));
            result.put("avgRating", BigDecimal.ZERO);
            result.put("totalCount", 0);
            return Result.success(result);
        }
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductReview::getProductId, productId)
               .eq(ProductReview::getStatus, 1)
               .orderByDesc(ProductReview::getCreateTime);
        if (rating != null && rating > 0) {
            wrapper.eq(ProductReview::getRating, rating);
        }
        if (hasVideo != null) {
            if (hasVideo) {
                wrapper.and(w -> w.isNotNull(ProductReview::getVideoUrls)
                        .apply("JSON_LENGTH(video_urls) > 0"));
            } else {
                wrapper.and(w -> w.isNull(ProductReview::getVideoUrls)
                        .or().apply("JSON_LENGTH(video_urls) = 0"));
            }
        }
        if (StringUtils.hasText(tag)) {
            wrapper.apply("JSON_CONTAINS(tags, JSON_QUOTE({0}))", tag.trim());
        }

        IPage<ProductReview> reviewPage = reviewMapper.selectPage(new Page<>(page, size), wrapper);
        Long currentUserId = currentUserIdNullable(request);
        reviewPage.getRecords().forEach(item -> {
            fillReviewUser(item);
            fillReviewMeta(item, currentUserId);
        });

        Map<String, Object> result = new HashMap<>();
        result.put("reviews", reviewPage);
        result.put("avgRating", reviewMapper.selectAvgRating(productId));
        result.put("totalCount", reviewMapper.selectReviewCount(productId));
        return Result.success(result);
    }

    @PostMapping
    public Result<?> createReview(@RequestBody ProductReview review, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("review");
        if (!moduleSwitchService.isEnabled("review")) {
            return Result.error("评价功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        review.setUserId(userId);
        review.setId(null);
        normalizeReviewPayload(review);

        if (review.getOrderId() == null || review.getProductId() == null) {
            throw new BusinessException("订单ID和商品ID不能为空");
        }
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            throw new BusinessException("评分必须在1-5之间");
        }

        Order order = orderMapper.selectById(review.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != Constants.OrderStatus.COMPLETED) {
            throw new BusinessException("只有已完成的订单才能评价");
        }

        long itemCount = orderItemMapper.selectCount(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, review.getOrderId())
                        .eq(OrderItem::getProductId, review.getProductId()));
        if (itemCount == 0) {
            throw new BusinessException("该商品不在此订单中");
        }

        long existCount = reviewMapper.selectCount(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getUserId, userId)
                        .eq(ProductReview::getOrderId, review.getOrderId())
                        .eq(ProductReview::getProductId, review.getProductId()));
        if (existCount > 0) {
            throw new BusinessException("您已评价过此商品");
        }

        Product product = productMapper.selectById(review.getProductId());
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        review.setStatus(1);
        review.setHelpfulCount(0);
        reviewMapper.insert(review);
        fillReviewMeta(review, userId);

        BigDecimal avgRating = reviewMapper.selectAvgRating(review.getProductId());
        Product update = new Product();
        update.setId(review.getProductId());
        update.setRating(avgRating.setScale(1, RoundingMode.HALF_UP));
        productMapper.updateById(update);

        tryCreateLowRatingTicket(review, order, product);

        if (product.getMerchantId() != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("scope", "review");
            payload.put("reviewId", review.getId());
            payload.put("productId", review.getProductId());
            payload.put("status", "pending-reply");
            managementWorkbenchRealtimeService.notifyMerchant(product.getMerchantId(), "review-updated", payload);
        }

        return Result.success("评价成功", review);
    }

    @PostMapping("/{id}/append")
    public Result<?> appendReview(@PathVariable Long id,
                                  @RequestBody Map<String, Object> payload,
                                  HttpServletRequest request) {
        moduleSwitchService.requireEnabled("review");
        if (!moduleSwitchService.isEnabled("review")) {
            return Result.error("评价功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("未登录");
        }

        ProductReview review = reviewMapper.selectById(id);
        if (review == null || !Objects.equals(review.getStatus(), 1)) {
            throw new BusinessException("评价不存在");
        }
        if (!Objects.equals(review.getUserId(), userId)) {
            throw new BusinessException("无权追评该评价");
        }

        String appendContent = trimToNull(payload.get("appendContent"));
        List<String> appendImages = normalizeStringList(payload.get("appendImages"));
        List<String> appendVideoUrls = normalizeStringList(payload.get("appendVideoUrls"));
        if (!StringUtils.hasText(appendContent) && appendImages.isEmpty() && appendVideoUrls.isEmpty()) {
            throw new BusinessException("追评内容不能为空");
        }

        ProductReview update = new ProductReview();
        update.setId(review.getId());
        update.setAppendContent(appendContent);
        update.setAppendImages(appendImages);
        update.setAppendVideoUrls(appendVideoUrls);
        update.setAppendTime(LocalDateTime.now());
        reviewMapper.updateById(update);

        ProductReview latest = reviewMapper.selectById(id);
        fillReviewUser(latest);
        fillReviewMeta(latest, userId);
        return Result.success("追评成功", latest);
    }

    @PostMapping("/{id}/helpful")
    public Result<?> markHelpful(@PathVariable Long id, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("review");
        if (!moduleSwitchService.isEnabled("review")) {
            return Result.error("评价功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("未登录");
        }

        ProductReview review = reviewMapper.selectById(id);
        if (review == null || !Objects.equals(review.getStatus(), 1)) {
            throw new BusinessException("评价不存在");
        }
        if (Objects.equals(review.getUserId(), userId)) {
            throw new BusinessException("不能给自己的评价点赞");
        }

        String deviceFingerprint = resolveDeviceFingerprint(request, userId);
        checkHelpfulVoteRisk(id, userId, deviceFingerprint);

        int inserted = reviewVoteMapper.insertIgnore(id, userId, deviceFingerprint);
        if (inserted > 0) {
            reviewMapper.increaseHelpfulCount(id);
        }

        ProductReview latest = reviewMapper.selectById(id);
        fillReviewUser(latest);
        fillReviewMeta(latest, userId);
        return Result.success(inserted > 0 ? "已标记有用" : "你已标记过有用", latest);
    }

    @DeleteMapping("/{id}/helpful")
    public Result<?> unmarkHelpful(@PathVariable Long id, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("review");
        if (!moduleSwitchService.isEnabled("review")) {
            return Result.error("评价功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException("未登录");
        }

        ProductReview review = reviewMapper.selectById(id);
        if (review == null || !Objects.equals(review.getStatus(), 1)) {
            throw new BusinessException("评价不存在");
        }

        int deleted = reviewVoteMapper.deleteByReviewAndUser(id, userId);
        if (deleted > 0) {
            reviewMapper.decreaseHelpfulCount(id);
        }

        ProductReview latest = reviewMapper.selectById(id);
        fillReviewUser(latest);
        fillReviewMeta(latest, userId);
        return Result.success(deleted > 0 ? "已取消有用" : "尚未标记有用", latest);
    }

    @GetMapping("/check")
    public Result<?> checkReviewable(
            @RequestParam Long orderId,
            @RequestParam Long productId,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("review");
        if (!moduleSwitchService.isEnabled("review")) {
            return Result.success(false);
        }
        Long userId = (Long) request.getAttribute("userId");
        long count = reviewMapper.selectCount(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getUserId, userId)
                        .eq(ProductReview::getOrderId, orderId)
                        .eq(ProductReview::getProductId, productId));
        return Result.success(count == 0);
    }

    @GetMapping("/my")
    public Result<?> myReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("review");
        if (!moduleSwitchService.isEnabled("review")) {
            return Result.success(emptyPage(page, size));
        }
        Long userId = (Long) request.getAttribute("userId");
        IPage<ProductReview> reviewPage = reviewMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getUserId, userId)
                        .orderByDesc(ProductReview::getCreateTime));
        reviewPage.getRecords().forEach(r -> {
            Product p = productMapper.selectById(r.getProductId());
            if (p != null) {
                r.setProductName(p.getName());
            }
        });
        return Result.success(reviewPage);
    }

    @GetMapping("/admin/low-rating")
    public Result<?> lowRatingReviews(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      HttpServletRequest request) {
        moduleSwitchService.requireEnabled("review");
        if (!moduleSwitchService.isEnabled("review")) {
            return Result.success(emptyPage(page, size));
        }
        ensureAdmin(request);

        IPage<ProductReview> reviewPage = reviewMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ProductReview>()
                        .le(ProductReview::getRating, 2)
                        .eq(ProductReview::getStatus, 1)
                        .orderByDesc(ProductReview::getCreateTime));
        reviewPage.getRecords().forEach(item -> {
            fillReviewUser(item);
            fillReviewMeta(item, currentUserIdNullable(request));
        });
        return Result.success(reviewPage);
    }

    private void tryCreateLowRatingTicket(ProductReview review, Order order, Product product) {
        if (review == null || review.getId() == null) {
            return;
        }
        if (review.getRating() == null || review.getRating() > LOW_RATING_THRESHOLD) {
            return;
        }

        ImTicket existing = imTicketMapper.selectOne(new LambdaQueryWrapper<ImTicket>()
                .eq(ImTicket::getReviewId, review.getId())
                .ne(ImTicket::getTicketStatus, "resolved")
                .orderByDesc(ImTicket::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }

        ImConversation conversation = findOrCreateLowRatingConversation(review, order, product);
        if (conversation == null || conversation.getId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        ImTicket ticket = new ImTicket();
        ticket.setConversationId(conversation.getId());
        ticket.setReviewId(review.getId());
        ticket.setTicketNo(generateNo("TK"));
        ticket.setTicketStatus("pending_assign");
        ticket.setSourceType("low_rating_auto");
        ticket.setIssueType("low_rating");
        ticket.setIssueSummary("低分评价自动介入");
        ticket.setIssueDetail(buildLowRatingIssueDetail(review, order, product));
        ticket.setCreatedByUserId(review.getUserId());
        ticket.setSlaDeadlineTime(now.plusMinutes(LOW_RATING_PENDING_TIMEOUT_MINUTES));
        ticket.setSlaEscalationLevel(0);
        ticket.setLastEscalationTime(null);
        imTicketMapper.insert(ticket);

        appendSystemMessage(conversation.getId(),
                "系统检测到低分评价（" + review.getRating() + " 星），已自动创建客服工单 " + ticket.getTicketNo() + " 并启动 SLA 计时。");
    }

    private ImConversation findOrCreateLowRatingConversation(ProductReview review, Order order, Product product) {
        LambdaQueryWrapper<ImConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImConversation::getConversationType, "support")
                .eq(ImConversation::getUserId, review.getUserId())
                .eq(review.getOrderId() != null, ImConversation::getOrderId, review.getOrderId())
                .eq(review.getProductId() != null, ImConversation::getProductId, review.getProductId())
                .orderByDesc(ImConversation::getId)
                .last("LIMIT 1");
        ImConversation conversation = imConversationMapper.selectOne(wrapper);
        if (conversation != null) {
            ImConversation update = new ImConversation();
            update.setId(conversation.getId());
            update.setStatus("pending_support");
            update.setIsEscalated(1);
            update.setPriority("urgent");
            update.setSupportAgentId(null);
            update.setClosedTime(null);
            imConversationMapper.updateById(update);
            return imConversationMapper.selectById(conversation.getId());
        }

        ImConversation created = new ImConversation();
        created.setConversationNo(generateNo("S"));
        created.setConversationType("support");
        created.setUserId(review.getUserId());
        created.setMerchantId(product == null ? null : product.getMerchantId());
        created.setSupportAgentId(null);
        created.setOrderId(review.getOrderId());
        created.setProductId(review.getProductId());
        created.setStatus("pending_support");
        created.setIsEscalated(1);
        created.setPriority("urgent");
        created.setUnreadUser(0);
        created.setUnreadMerchant(0);
        created.setUnreadSupport(1);
        imConversationMapper.insert(created);
        return imConversationMapper.selectById(created.getId());
    }

    private String buildLowRatingIssueDetail(ProductReview review, Order order, Product product) {
        StringBuilder sb = new StringBuilder();
        sb.append("诉求：用户提交低分评价，需客服介入核实处理。");
        sb.append("\n已核实信息：");
        sb.append("\n- reviewId: ").append(review.getId());
        sb.append("\n- 评分: ").append(review.getRating()).append(" 星");
        if (order != null) {
            sb.append("\n- 订单号: ").append(order.getOrderNo());
        }
        if (product != null) {
            sb.append("\n- 商品: ").append(product.getName());
        }
        String content = trimToNull(review.getContent());
        if (StringUtils.hasText(content)) {
            sb.append("\n- 评价内容: ").append(content);
        }
        sb.append("\n建议动作：优先联系商家和用户，确认补发/退款/赔付方案并在工单内回填处理结论。");
        return sb.toString();
    }

    private void appendSystemMessage(Long conversationId, String content) {
        if (conversationId == null || !StringUtils.hasText(content)) {
            return;
        }
        ImMessage message = new ImMessage();
        message.setConversationId(conversationId);
        message.setSenderRole("system");
        message.setSenderId(0L);
        message.setMessageType("system");
        message.setContent(content.trim());
        message.setIsSystem(1);
        imMessageMapper.insert(message);

        ImConversation update = new ImConversation();
        update.setId(conversationId);
        update.setLastMessage(content.trim());
        update.setLastMessageType("system");
        update.setLastSenderRole("system");
        update.setLastSenderId(0L);
        update.setLastMessageTime(message.getCreateTime() == null ? LocalDateTime.now() : message.getCreateTime());
        ImConversation latest = imConversationMapper.selectById(conversationId);
        update.setUnreadSupport(latest == null ? 1 : ((latest.getUnreadSupport() == null ? 0 : latest.getUnreadSupport()) + 1));
        imConversationMapper.updateById(update);
    }

    private String resolveDeviceFingerprint(HttpServletRequest request, Long userId) {
        if (request != null) {
            String fingerprint = trimToNull(request.getHeader("X-Device-Fingerprint"));
            if (!StringUtils.hasText(fingerprint)) {
                fingerprint = trimToNull(request.getHeader("X-Device-Id"));
            }
            if (StringUtils.hasText(fingerprint)) {
                return fingerprint;
            }
            String userAgent = trimToNull(request.getHeader("User-Agent"));
            if (StringUtils.hasText(userAgent) && userAgent.length() >= 8) {
                return "ua:" + Integer.toHexString(userAgent.hashCode());
            }
        }
        return userId == null ? null : ("uid:" + userId);
    }

    private void checkHelpfulVoteRisk(Long reviewId, Long userId, String deviceFingerprint) {
        long userRate = increaseCounter(HELPFUL_RATE_KEY_PREFIX + "user:" + userId, HELPFUL_USER_WINDOW_SECONDS);
        if (userRate > HELPFUL_USER_MAX_REQUESTS) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        if (!StringUtils.hasText(deviceFingerprint)) {
            return;
        }
        long deviceRate = increaseCounter(HELPFUL_RATE_KEY_PREFIX + "device:" + deviceFingerprint, HELPFUL_DEVICE_WINDOW_SECONDS);
        if (deviceRate > HELPFUL_DEVICE_MAX_REQUESTS) {
            blockSuspiciousDevice(deviceFingerprint, "评价有用投票设备频率异常");
            throw new BusinessException("当前设备操作异常，已触发风控限制");
        }

        long hourlyCount = increaseCounter(HELPFUL_HOURLY_KEY_PREFIX + deviceFingerprint, 3600);
        if (hourlyCount > HELPFUL_DEVICE_HOURLY_LIMIT) {
            blockSuspiciousDevice(deviceFingerprint, "评价有用投票设备小时峰值异常");
            throw new BusinessException("当前设备操作异常，已触发风控限制");
        }

        long bucket = System.currentTimeMillis() / (HELPFUL_DEVICE_UNIQUE_USER_WINDOW_SECONDS * 1000L);
        String deviceUserSetKey = HELPFUL_DEVICE_USER_SET_PREFIX + deviceFingerprint + ":" + bucket;
        redisUtil.addToSet(deviceUserSetKey, String.valueOf(userId));
        redisUtil.expire(deviceUserSetKey, HELPFUL_DEVICE_UNIQUE_USER_WINDOW_SECONDS * 2L, TimeUnit.SECONDS);
        long uniqueUsers = redisUtil.getSetSize(deviceUserSetKey);
        if (uniqueUsers > HELPFUL_DEVICE_MAX_UNIQUE_USERS) {
            blockSuspiciousDevice(deviceFingerprint, "评价有用投票设备账号聚集异常");
            throw new BusinessException("检测到异常投票行为，操作已拦截");
        }

        long sameReviewDeviceRate = increaseCounter(
                HELPFUL_RATE_KEY_PREFIX + "review:" + reviewId + ":device:" + deviceFingerprint,
                HELPFUL_USER_WINDOW_SECONDS);
        if (sameReviewDeviceRate > 6) {
            throw new BusinessException("同设备投票过于频繁，请稍后再试");
        }
    }

    private long increaseCounter(String key, int windowSeconds) {
        Long value = redisUtil.incr(key, 1);
        if (value != null && value == 1L) {
            redisUtil.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return value == null ? 0L : value;
    }

    private void blockSuspiciousDevice(String deviceFingerprint, String reason) {
        if (!StringUtils.hasText(deviceFingerprint)) {
            return;
        }
        try {
            riskControlService.addBlacklist(
                    "DEVICE",
                    deviceFingerprint,
                    3600,
                    reason,
                    "AUTO",
                    "review-risk",
                    "review_helpful_vote"
            );
        } catch (Exception ignore) {
            // ignore
        }
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private void fillReviewUser(ProductReview review) {
        User user = userMapper.selectById(review.getUserId());
        if (user != null) {
            String displayName = (user.getNickname() != null && !user.getNickname().isEmpty())
                    ? user.getNickname() : user.getUsername();
            if (displayName != null && displayName.length() > 2) {
                review.setUsername(displayName.charAt(0) + "***" + displayName.charAt(displayName.length() - 1));
            } else {
                review.setUsername(displayName);
            }
            review.setAvatar(user.getAvatar());
        }
    }

    private void fillReviewMeta(ProductReview review, Long currentUserId) {
        if (review == null) {
            return;
        }
        if (review.getHelpfulCount() == null) {
            review.setHelpfulCount(0);
        }
        boolean voted = false;
        if (currentUserId != null && currentUserId > 0) {
            voted = reviewVoteMapper.countByReviewAndUser(review.getId(), currentUserId) > 0;
        }
        review.setHelpfulVoted(voted);
    }

    private void normalizeReviewPayload(ProductReview review) {
        if (review == null) {
            return;
        }
        review.setContent(trimToNull(review.getContent()));
        review.setImages(normalizeStringList(review.getImages()));
        review.setVideoUrls(normalizeStringList(review.getVideoUrls()));
        review.setTags(normalizeStringList(review.getTags()));
        review.setAppendContent(null);
        review.setAppendImages(Collections.emptyList());
        review.setAppendVideoUrls(Collections.emptyList());
        review.setAppendTime(null);
        if (review.getHelpfulCount() == null) {
            review.setHelpfulCount(0);
        }
    }

    private List<String> normalizeStringList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<?> source;
        if (raw instanceof List<?>) {
            source = (List<?>) raw;
        } else {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : source) {
            String text = trimToNull(item);
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long currentUserIdNullable(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId instanceof Long ? (Long) userId : null;
    }

    private void ensureAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        if (role == null || !Constants.Role.ADMIN.equals(String.valueOf(role))) {
            throw new BusinessException("无权操作");
        }
    }

    private <T> IPage<T> emptyPage(int page, int size) {
        Page<T> empty = new Page<>(page, size);
        empty.setRecords(java.util.Collections.emptyList());
        empty.setTotal(0);
        return empty;
    }
}
