package com.ecommerce.common;

public class Constants {

    public interface ErrorCode {
        int MODULE_DISABLED = 423;
    }

    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    // ========== 订单限流配置 ==========
    public interface OrderRateLimit {
        /** 订单创建频率限制：时间窗口（分钟） */
        int DUPLICATE_CHECK_WINDOW_MINUTES = 1;
        /** 订单创建频率限制：窗口内最大数量 */
        int MAX_PENDING_ORDERS_PER_WINDOW = 5;
        /** 订单超时时间（分钟） */
        int DEFAULT_TIMEOUT_MINUTES = 30;
    }

    // ========== 推荐算法配置 ==========
    public interface Recommendation {
        /** 冷启动用户行为阈值 */
        int COLD_START_BEHAVIOR_THRESHOLD = 5;
        /** 默认推荐数量 */
        int DEFAULT_TOP_N = 20;
        /** 热门商品候选池倍数 */
        int HOT_CANDIDATE_POOL_MULTIPLIER = 4;
        /** 最小热门候选池大小 */
        int MIN_HOT_CANDIDATE_POOL = 40;
    }

    // ========== 分页配置 ==========
    public interface Pagination {
        int DEFAULT_PAGE_SIZE = 10;
        int MAX_PAGE_SIZE = 100;
    }

    public interface Role {
        String ADMIN = "admin";
        String MERCHANT = "merchant";
        String USER = "user";
    }

    public interface OrderStatus {
        int PENDING = 0;
        int PAID = 1;
        int SHIPPED = 2;
        int COMPLETED = 3;
        int CANCELLED = 4;
        int REFUNDED = 5;
    }

    public interface BehaviorType {
        String VIEW = "view";
        String CART = "cart";
        String PURCHASE = "purchase";
        String FAVORITE = "favorite";
        String DISLIKE = "dislike";
        String SEARCH = "search";
    }

    public interface RecommendationEventType {
        String EXPOSURE = "exposure";
        String CLICK = "click";
        String DWELL = "dwell";
        String ADD_CART = "add_cart";
        String ORDER = "order";
        String REFUND = "refund";
    }

    public interface ProductStatus {
        int OFF_SHELF = 0;
        int ON_SHELF = 1;
    }

    public interface WalletType {
        String RECHARGE = "recharge";
        String PAY = "pay";
        String REFUND = "refund";
    }

    public interface LoginType {
        String PASSWORD = "password";
        String PHONE_CODE = "phone_code";
    }

    public interface CouponType {
        int FULL_REDUCTION = 1;
        int DISCOUNT = 2;
        int NO_THRESHOLD = 3;
    }

    public interface CouponStatus {
        int NOT_STARTED = 0;
        int ACTIVE = 1;
        int ENDED = 2;
    }

    public interface CouponScope {
        int PLATFORM = 0;
        int MERCHANT_STORE = 1;
    }

    public interface SeckillPublishStatus {
        int OFFLINE = 0;
        int PUBLISHED = 1;
    }

    public interface SeckillAuditStatus {
        int PENDING = 0;
        int APPROVED = 1;
        int REJECTED = 2;
        int REVOKED = 3;
    }

    public interface SeckillRuntimeStatus {
        int UPCOMING = 0;
        int ACTIVE = 1;
        int ENDED = 2;
        int SOLD_OUT = 3;
    }

    public interface ReviewStatus {
        int PENDING = 0;
        int APPROVED = 1;
        int REJECTED = 2;
    }

    public interface RefundStatus {
        int PENDING = 0;
        int APPROVED = 1;
        int REJECTED = 2;
        int REFUNDED = 3;
    }

    public interface MessageType {
        String ORDER = "order";
        String SYSTEM = "system";
        String PROMOTION = "promotion";
    }

    public interface RedisKey {
        String USER_TOKEN = "user:token:";
        String RECOMMENDATION_PERSONAL = "recommend:personal:";
        String RECOMMENDATION_HOT = "recommend:hot";
        String PRODUCT_DETAIL = "product:detail:";
        String USER_BEHAVIOR_COUNT = "behavior:count:";
        String VERIFY_CODE = "verify:code:";
        String VERIFY_LIMIT = "verify:limit:";
        String VERIFY_IP_MINUTE = "verify:ip:minute:";
        String VERIFY_IP_HOUR = "verify:ip:hour:";
        String CAPTCHA_CODE = "captcha:code:";
    }
}
