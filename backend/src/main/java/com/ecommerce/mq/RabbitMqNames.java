package com.ecommerce.mq;

public final class RabbitMqNames {

    private RabbitMqNames() {
    }

    public static final String EVENT_EXCHANGE = "ecommerce.event.exchange";
    public static final String DELAY_EXCHANGE = "ecommerce.delay.exchange";
    public static final String DLX_EXCHANGE = "ecommerce.dlx.exchange";

    public static final String NOTIFICATION_QUEUE = "ecommerce.notification.queue";
    public static final String NOTIFICATION_DLQ = "ecommerce.notification.dlq";
    public static final String NOTIFICATION_DLQ_ROUTING_KEY = "ecommerce.notification.dlq";

    public static final String BEHAVIOR_QUEUE = "ecommerce.behavior.queue";
    public static final String BEHAVIOR_DLQ = "ecommerce.behavior.dlq";
    public static final String BEHAVIOR_DLQ_ROUTING_KEY = "ecommerce.behavior.dlq";

    public static final String BROADCAST_QUEUE = "ecommerce.broadcast.queue";
    public static final String BROADCAST_DLQ = "ecommerce.broadcast.dlq";
    public static final String BROADCAST_DLQ_ROUTING_KEY = "ecommerce.broadcast.dlq";

    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "ecommerce.order.timeout.delay.queue";
    public static final String ORDER_TIMEOUT_CHECK_QUEUE = "ecommerce.order.timeout.check.queue";
    public static final String ORDER_TIMEOUT_CHECK_DLQ = "ecommerce.order.timeout.check.dlq";
    public static final String ORDER_TIMEOUT_CHECK_DLQ_ROUTING_KEY = "ecommerce.order.timeout.check.dlq";

    public static final String ROUTING_ORDER_CREATED = "order.created";
    public static final String ROUTING_ORDER_PAID = "order.paid";
    public static final String ROUTING_ORDER_CANCELLED = "order.cancelled";
    public static final String ROUTING_ORDER_TIMEOUT_CANCELLED = "order.timeout.cancelled";
    public static final String ROUTING_ORDER_STATUS_CHANGED = "order.status.changed";
    public static final String ROUTING_ORDER_TIMEOUT_SCHEDULE = "order.timeout.schedule";
    public static final String ROUTING_ORDER_TIMEOUT_CHECK = "order.timeout.check";
    public static final String ROUTING_REFUND_APPLIED = "refund.applied";
    public static final String ROUTING_REFUND_REFUNDED = "refund.refunded";
    public static final String ROUTING_REFUND_REJECTED = "refund.rejected";
    public static final String ROUTING_MESSAGE_BROADCAST = "message.broadcast";
    public static final String ROUTING_COUPON_ASSIGNED = "coupon.assigned";
}
