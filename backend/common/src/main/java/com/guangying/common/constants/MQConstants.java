package com.guangying.common.constants;

/**
 * RocketMQ 常量定义
 *
 * <p>RocketMQ 模型：Topic + Tag + ConsumerGroup</p>
 * <p>命名规范：{业务域}_{动作}_{类型}</p>
 */
public final class MQConstants {

    private MQConstants() {}

    // =================== Topic ===================
    /** 订单事件 Topic（创建/支付/取消/退款） */
    public static final String ORDER_TOPIC = "guangying_order_event";

    /** 订单超时检查 Topic（RocketMQ 5 定时消息，仅承载延时类型消息） */
    public static final String ORDER_TIMEOUT_TOPIC = "guangying_order_timeout";

    /** 想看写回 Topic */
    public static final String WISH_TOPIC = "guangying_wish_writeback";

    /** 缓存失效 Topic */
    public static final String CACHE_TOPIC = "guangying_cache_invalidate";

    // =================== Tag ===================
    public static final String TAG_ORDER_CREATED = "ORDER_CREATED";
    public static final String TAG_ORDER_PAID = "ORDER_PAID";
    public static final String TAG_ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String TAG_ORDER_TIMEOUT_CHECK = "ORDER_TIMEOUT_CHECK";
    public static final String TAG_ORDER_REFUNDED = "ORDER_REFUNDED";
    public static final String TAG_WISH_WRITEBACK = "WISH_WRITEBACK";
    public static final String TAG_CACHE_INVALIDATE = "CACHE_INVALIDATE";

    // =================== Consumer Group ===================
    public static final String ORDER_CONSUMER_GROUP = "guangying_order_consumer_group";
    public static final String ORDER_TIMEOUT_CONSUMER_GROUP = "guangying_order_timeout_consumer_group";
    public static final String WISH_CONSUMER_GROUP = "guangying_wish_consumer_group";
    public static final String CACHE_CONSUMER_GROUP = "guangying_cache_consumer_group";
}
