package com.guangying.common.constants;

/**
 * 缓存常量（多级缓存 & Redis Key 规范）
 *
 * <p>Key 设计规范：{业务域}:{数据类型}:{唯一标识}</p>
 */
public final class CacheConstants {

    private CacheConstants() {}

    // =================== L1 / L2 缓存 Key ===================

    /** 热映电影列表缓存键 */
    public static final String HOT_MOVIES = "hot_movies";

    /** 即将上映列表缓存键 */
    public static final String COMING_MOVIES = "coming_movies";

    /** 最受期待列表缓存键 */
    public static final String MOST_EXPECTED = "most_expected";

    /** 城市列表缓存键 */
    public static final String CITIES = "cities";

    /** 影院筛选项缓存前缀 */
    public static final String CINEMA_FILTER_PREFIX = "cinema_filter:";

    // =================== Redis 专用 Key ===================

    /** 电影想看数 Hash: movie:wish → {movieId: count} */
    public static final String MOVIE_WISH_HASH = "movie:wish";

    /** 用户想看集合: user:wish:{userId} */
    public static final String USER_WISH_PREFIX = "user:wish:";

    /** 限流 Key 前缀: rate_limit:{resource}:{identifier} */
    public static final String RATE_LIMIT_PREFIX = "rate_limit:";

    /** 分布式锁前缀: lock:{resource} */
    public static final String LOCK_PREFIX = "lock:";

    /** 电影详情缓存: movie:detail:{movieId} */
    public static final String MOVIE_DETAIL_PREFIX = "movie:detail:";

    // =================== 座位锁（单座 Key，目标架构核心） ===================

    /** 座位锁 Key: seat:lock:{scheduleId}:{row}_{col} — 单座独立 TTL */
    public static final String SEAT_LOCK_KEY_PREFIX = "seat:lock:";

    /** 已售座位投影 Set: seat:sold:{scheduleId} — DB 的只读副本 */
    public static final String SEAT_SOLD_SET_PREFIX = "seat:sold:";

    /** 锁定座位辅助 Set: seat:locked:{scheduleId} — 渲染加速，非权威 */
    public static final String SEAT_LOCKED_SET_PREFIX = "seat:locked:";

    /** lockToken 映射: seat:locktoken:{token} → {userId, scheduleId, seats} */
    public static final String SEAT_LOCK_TOKEN_PREFIX = "seat:locktoken:";

    /** 选座图渲染缓存: seat:layout:rendered:{scheduleId} — 3~5s TTL */
    public static final String SEAT_LAYOUT_RENDERED_PREFIX = "seat:layout:rendered:";

    /** 余票计数器: seat:count:{scheduleId} — 仅展示用，不参与锁座前置门 */
    public static final String SEAT_COUNT_PREFIX = "seat:count:";

    /** 热门场次标记: schedule:hot:{scheduleId} */
    public static final String SCHEDULE_HOT_KEY = "schedule:hot";

    /** 热门场次准入上限: queue:admission:{scheduleId} — 当前已入场人数 */
    public static final String QUEUE_ADMISSION_PREFIX = "queue:admission:";

    /** 热门场次准入上限: queue:max:{scheduleId} — 最大同时入场人数 */
    public static final String QUEUE_MAX_PREFIX = "queue:max:";

    /** 排队等候队列: queue:waiting:{scheduleId} — ZSet, member=userId, score=入场时间戳 */
    public static final String QUEUE_WAITING_PREFIX = "queue:waiting:";

    /** 入场令牌: queue:token:{scheduleId}:{userId} — String "1", 带 TTL */
    public static final String QUEUE_TOKEN_PREFIX = "queue:token:";

    /** 入场租约: queue:lease:{scheduleId} — ZSet, member=userId, score=租约到期毫秒时间戳 */
    public static final String QUEUE_LEASE_PREFIX = "queue:lease:";

    /** 所有启用 Waiting Room 的场次 ID，用于定时回收过期租约 */
    public static final String QUEUE_HOT_SCHEDULES_KEY = "queue:hot:schedules";

    /** 入场令牌 TTL（秒）：10 分钟内完成锁座，否则自动释放名额 */
    public static final int QUEUE_TOKEN_TTL_SECONDS = 600;

    /** 排队等候预估每人等待时间（秒） */
    public static final int QUEUE_ESTIMATED_WAIT_PER_PERSON = 30;

    // =================== 已废弃（保留兼容旧代码） ===================

    /** @deprecated 使用 SEAT_LOCK_KEY_PREFIX 替代 */
    @Deprecated
    public static final String SEAT_LOCK_PREFIX = "seat:lock:";

    /** @deprecated MQ 缓冲方案已移除 */
    @Deprecated
    public static final String SEAT_REQUEST_RESULT_PREFIX = "seat:request:result:";

    /** @deprecated MQ 缓冲方案已移除 */
    @Deprecated
    public static final String SEAT_QUEUE_DEPTH_KEY = "seat:queue:depth";

    /** 座位锁定过期时间(分钟) */
    public static final int SEAT_LOCK_MINUTES = 15;

    /** 抢座请求最大排队数 */
    public static final int SEAT_QUEUE_MAX_DEPTH = 200;

    /** 抢座请求结果缓存过期时间(秒) */
    public static final int SEAT_REQUEST_RESULT_TTL = 30;

    /** 订单支付超时时间(分钟) */
    public static final int ORDER_PAY_TIMEOUT_MINUTES = 15;

    // =================== 过期时间 ===================

    /** L1 Caffeine 默认过期时间(秒) */
    public static final long L1_EXPIRE_SECONDS = 60;

    /** L2 Redis 默认过期时间(分钟) */
    public static final long L2_EXPIRE_MINUTES = 10;

    /** 热点缓存重建锁最大等待时间（秒），超时后优先保证接口可用性 */
    public static final long CACHE_REBUILD_LOCK_WAIT_SECONDS = 2;

    /** 空值缓存过期时间（秒），拦截不存在 ID 的重复查询，同时缩短数据恢复窗口 */
    public static final long CACHE_NULL_EXPIRE_SECONDS = 60;

    /** 默认缓存过期时间(分钟) */
    public static final long DEFAULT_EXPIRE_MINUTES = 10;

    /** 城市列表缓存过期时间(小时) */
    public static final long CITY_EXPIRE_HOURS = 24;

}
