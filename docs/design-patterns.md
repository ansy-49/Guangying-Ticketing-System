# 光影票务中的设计模式

## 1. 限流：策略模式 + 注册表模式

限流切面只负责提取资源、用户标识和注解参数，不再通过 if/else 选择算法。

    @RateLimit
        ↓
    RateLimitAspect
        ↓
    RateLimitStrategyRegistry
        ├── SlidingWindowRateLimitStrategy
        └── TokenBucketRateLimitStrategy

核心角色：

- RateLimitContext：封装一次限流判断需要的稳定输入。
- RateLimitStrategy：所有限流算法的统一契约。
- RateLimitStrategyRegistry：在启动时收集 Spring Bean，按 RateLimitAlgorithm 路由，并拒绝重复注册。
- SlidingWindowRateLimitStrategy：适配 Redis ZSet + Lua 滑动窗口。
- TokenBucketRateLimitStrategy：适配 Redis String + Lua 令牌桶。

扩展新算法时，只需要：

1. 在 RateLimitAlgorithm 中增加枚举。
2. 新增 RateLimitStrategy 实现并标记为 Spring Component。
3. 在注解上选择新算法。

RateLimitAspect 无需修改，符合开闭原则。

## 2. 订单事件：命令模式 + 注册表模式

RocketMQ 消费者只负责反序列化、基础校验和路由，不再维护不断增长的 switch。

    RocketMQ
        ↓
    OrderEventConsumer
        ↓
    OrderEventHandlerRegistry
        ├── OrderCreatedEventHandler
        ├── OrderPaidEventHandler
        └── OrderCancelledEventHandler

核心角色：

- OrderEventHandler：一个事件命令对应一个处理器。
- OrderEventHandlerRegistry：按 eventType 路由，并在启动时检查重复处理器。
- OrderEventProjectionSupport：封装多个处理器共享的 Redis 只读投影操作。
- 各事件处理器：只依赖处理本事件所需的 Mapper 或 Service。

新增退款事件时，只需新增 OrderRefundedEventHandler，无需修改消费者，降低修改既有分支造成回归的风险。

## 3. 登录：责任链模式

登录流程由 `LoginValidationChain` 按顺序执行参数规范化、账号限流、用户查询和 BCrypt 密码校验。每个节点只处理一种职责，校验失败立即中断，新增账号状态、验证码或风控节点时无需修改 `UserService`。

    LoginValidationChain
        ├── LoginRequestValidationHandler
        ├── LoginRateLimitHandler
        ├── LoginUserLookupHandler
        └── LoginPasswordVerificationHandler

## 4. Web 请求：拦截器链

Spring MVC 按明确顺序执行三个拦截器：`RequestContextInterceptor` 生成并清理 Request ID，`RequestLogInterceptor` 记录耗时，`JwtAuthInterceptor` 完成身份校验并把 userId 写入请求上下文。Controller 只消费认证结果，不再重复解析 Token。

    RequestContextInterceptor (order 0)
        → RequestLogInterceptor (order 10)
        → JwtAuthInterceptor (order 20)

## 5. 为什么没有使用订单状态模式

订单状态转换最终依赖数据库条件更新，例如 WAIT_PAY → PAID。这里的关键是并发 CAS 和受影响行数，而不是在 JVM 内切换对象状态。

强行套用经典 State 对象会增加类数量，却不能替代数据库并发约束。因此当前保留显式状态机 SQL，把设计模式用于真正存在扩展分支的位置。
