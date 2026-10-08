package com.guangying.common.enums;

/**
 * 限流维度。
 *
 * <p>限流算法回答“怎样计数”，维度回答“对谁或哪个资源计数”。两者正交，
 * 避免只有单用户限流却误以为能够保护热门资源。</p>
 */
public enum RateLimitDimension {

    /** 已登录用户优先，匿名请求回退到客户端 IP。 */
    CALLER,

    /** 从方法参数中提取业务资源，例如 scheduleId。 */
    RESOURCE,

    /** 当前资源在整个应用集群共享一个桶。 */
    GLOBAL
}
