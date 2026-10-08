# 15. 面试讲解手册

## 1. 面试表达原则

讲项目时按“业务问题 -> 技术选择 -> 关键实现 -> 结果证据 -> 边界与备选方案”展开。不要把技术名词平铺成清单，也不要把未实现的生产能力说成已经完成。

可信表达包含三类信息：

- 为什么：原方案遇到什么具体问题。
- 怎么做：关键数据结构、事务边界和失败处理。
- 凭什么：代码入口、测试、压测或数据库约束。

## 2. 一分钟介绍

> 光影票务是我独立设计并实现的电影购票交易系统，覆盖电影与影院查询、热门场次排队、在线锁座、订单创建和积分支付。查询侧使用 Caffeine + Redis 两级 Cache Aside，并通过空值缓存、随机 TTL、本地 single-flight 和 Redisson 分布式锁分别治理穿透、雪崩和击穿；在 3000 请求、100 并发的同环境单实例 A/B 压测中，电影列表吞吐提升 73.5%。交易侧构建入口限流、Waiting Room 准入、Redis Lua 批量锁座、MySQL 条件扣减和唯一索引多层防线，并用事务回调处理 Redis 补偿。消息侧使用 Transactional Outbox + RocketMQ，通过发送租约、指数退避和消费端 eventId 去重实现 At-least-once 下的最终一致性。项目采用多模块单体，保留本地事务和简单部署，同时把代码边界拆清楚。

## 3. 三分钟讲解结构

### 3.1 背景

普通电影展示项目区分度低，所以把重点放到购票交易：热门读、开售洪峰、座位并发、订单与消息双写。

### 3.2 第一条：缓存

电影列表读多写少，先用 Caffeine 降低进程内延迟，再用 Redis 跨实例共享。缓存 Miss 时以本地 single-flight 和 Redisson 锁合并回源，持锁后二次检查；空值缓存、随机 TTL 和故障降级分别处理穿透、雪崩与 Redis 异常。

### 3.3 第二条：锁座

旧的场次级乐观锁让不同座位也争同一版本号。现在先用令牌桶和 Waiting Room 削峰，再用 Lua 对一组座位原子预检和写锁，数据库通过库存条件更新和座位唯一索引最终裁决。Redis 锁成功但事务失败时，在 afterRollback 释放幽灵锁。

### 3.4 第三条：消息

订单提交后直接发 MQ 会有崩溃丢消息窗口，因此订单、创建事件和超时检查事件与 Outbox 同事务落库。发送器通过条件更新抢占事件、设置发送租约并重试；超时检查进入专用 DELAY Topic。MQ 至少一次投递，消费者用 `processed_event` 唯一键去重，再由 Handler 注册表路由。定时消息、支付懒过期与 DB 扫描最终调用同一个 CAS 关单服务。

### 3.5 结尾边界

当前是多模块单体和积分模拟支付，本地模式用 H2，完整 Docker 模式才接入 MySQL、Redis、RocketMQ。没有把它包装成生产级支付或微服务系统。

## 4. 简历三条与代码证据

| 简历亮点 | 必看代码 | 可验证证据 |
| --- | --- | --- |
| 两级缓存与 73.5% 吞吐提升 | `MultiLevelCacheService`、`DistributedLockService` | 缓存测试、A/B JSON 和报告 |
| 四级防线与防超卖 | `RateLimitAspect`、`QueueService`、`SeatLockScriptService`、`SeatService` | 主流程测试、seat-race 结果、唯一索引 |
| Outbox + RocketMQ 最终一致 | `OutboxService`、`OutboxEventMapper`、`OrderEventConsumer`、Handler Registry | 抢占/DEAD 测试、processed_event 约束 |

## 5. 面试官只看三处代码

### 5.1 `SeatService#lockSeatsAndCreateOrder`

能展示幂等键、请求指纹、Redisson 有界锁、Waiting Room、Lua、数据库事务、Outbox 与事务补偿如何串成一个完整用例。

### 5.2 `MultiLevelCacheService#get`

能展示模板骨架、L1/L2、空值、single-flight、分布式锁、二次检查和故障降级。

### 5.3 `OutboxService` + `OrderEventConsumer`

能展示生产端可靠投递、租约抢占、指数退避、至少一次语义和消费端幂等。

如果只能严格选三个文件，选 `SeatService.java`、`MultiLevelCacheService.java`、`OutboxService.java`，再口头补充消费者。

## 6. 高频追问与回答框架

### 6.1 为什么不做微服务

回答重点：当前订单、库存、积分适合一个 MySQL 本地事务；团队和流量不需要服务治理成本；多模块先解决代码边界，达到独立扩缩容/团队/数据所有权条件再拆。

### 6.2 Redis 和 MySQL 谁说了算

回答重点：MySQL 是订单、库存、积分、成交座位权威；Redis 是缓存、租约、临时锁和投影。Redis 用于快速拒绝和削峰，数据库条件更新与唯一索引最终裁决。

### 6.3 Lua 锁座后为什么还会失败

回答重点：Lua 只说明 Redis 中没有竞争，数据库还可能库存不足、唯一约束冲突或事务失败；所以必须回滚并补偿 Redis 锁。

### 6.4 Waiting Room 是不是直接拒绝

回答重点：预热为热门场次后，有容量立即发 10 分钟入场令牌，无容量进入 ZSet 并展示位置，队列达到默认 100000 才拒绝。入场只允许尝试锁座，不保留具体座位。

### 6.5 maxAdmission 到底是多少

回答重点：默认 2000 个活跃准入租约，管理员可以按场次覆盖；它控制的是正在选座和尝试下单的活跃会话，不是 2000 QPS。锁座另有用户 `8/2s`、场次 `400/120s`、全局 `1200/400s` 三级令牌桶。所有参数都是集群参考基线，最终要依据锁座事务 P95、连接池、数据库写吞吐和转化率压测回标。

### 6.6 为什么 token TTL 还需要 lease

回答重点：token 到期会自动删除，但不会通知系统减少入场计数；lease ZSet 保存到期时间，5 秒回收任务删除过期租约并晋升下一位。

### 6.7 为什么 Outbox 仍可能重复消息

回答重点：Broker 接收成功到数据库标 SENT 之间仍有崩溃窗口，所以选择 At-least-once；消费者用 eventId 唯一键与业务处理同事务去重。

### 6.8 RocketMQ 提升了多少毫秒

回答重点：当前没有做严格 A/B，不能给毫秒数字。RocketMQ + Outbox 的主要价值是解耦和可靠性，缓存 73.5% 才是有明确实验口径的性能数据。

## 7. 容易被追问的薄弱点

### 7.1 邀请码硬编码

直接承认是演示配置。生产会数据化并保存摘要，增加过期、次数和审计。不要把它解释成安全设计。

### 7.2 JWT 没有 Refresh Token

直接说明当前是 72 小时 Access Token，无撤销能力。生产采用短 Access + Refresh 轮换、jti/会话版本与设备管理。

### 7.3 H2 测试

说明 H2 只验证业务链路，不证明 MySQL 行锁和优化器；Docker/未来 Testcontainers 用于真实中间件验证。

### 7.4 Redis `allkeys-lru`

承认 2 GB Compose 是演示配置，内存压力可能淘汰锁与队列 key。生产应隔离关键租约与普通缓存、设置容量告警和更合适策略。

### 7.5 provider 直接调用 service

说明当前查询聚合使用 biz，交易用例仍在 service，Maven 传递依赖没有强制边界。演进可用 Application Facade 和 ArchUnit 限制越层。

### 7.6 投影更新失败

说明数据库已经提交，不能因 Redis 失败回滚真实订单。支付 Outbox 会重建已售投影，读取也能回源 DB；生产再增加对账和失败指标。

## 8. 备选方案对比

| 当前方案 | 备选 | 为什么当前没选 |
| --- | --- | --- |
| 多模块单体 | 微服务 | 本地事务和部署成本更适合当前规模 |
| Caffeine + Redis | 仅 Redis | 本地热点仍承担网络开销 |
| 空值缓存 | Bloom Filter | 当前规模小，空值缓存更直接；Bloom 有误判和重建成本 |
| 条件更新 | 场次乐观锁 | 不同座位产生无关版本冲突 |
| Lua + DB 兜底 | 只用 DB 悲观锁 | 洪峰时连接和锁等待压力更大 |
| Outbox | MQ 事务消息 | Outbox 更易观察和人工恢复，且不把业务绑定到特定 Broker 事务机制 |
| 定时消息 + 懒过期 + DB 扫描 | 只用延时消息或只轮询 | 消息降低关单延迟，扫描补漏；统一 CAS 关单避免重复返库存 |
| At-least-once + 幂等 | 声称 exactly-once | 跨 DB、MQ、Redis 的端到端 exactly-once 不现实 |

## 9. 数据回答口径

可直接说：

> 在同一台机器、同一 JAR、同一 H2 数据集、预热 100 次、正式 3000 请求和 100 并发下，对电影列表接口只切换缓存开关。吞吐从 2470.32 RPS 提升到 4287.08 RPS，提升 73.54%，成功率均为 100%。这个数字用于说明热点列表缓存有效，不代表线上容量，也不能全部归因于 Redis，因为热路径主要命中 Caffeine。

这段话同时给出了环境、变量、结果和边界，比只说“提升 73.5%”更可信。

## 10. 不应使用的表述

- “项目是微服务架构。”
- “使用 Redis 保证绝对不超卖。”
- “RocketMQ 保证 exactly-once。”
- “Waiting Room 能抗任意高并发。”
- “压测证明系统线上能跑 4287 QPS。”
- “使用了分布式事务。”
- “实现了真实支付和退款。”
- “Redis 挂了所有能力都无感。”

## 11. 演示顺序

1. 首页查询电影和影院。
2. 注册登录，说明 BCrypt、责任链和 JWT。
3. 管理员初始化热门场次。
4. 两个用户展示排队位置和入场令牌。
5. 两个用户抢同一座位，展示一个成功、一个冲突。
6. 查看 PENDING 订单并完成积分支付。
7. 查看 MySQL 中 order、order_seat、outbox_event、processed_event。
8. 停止 RocketMQ 后建单，恢复后展示 Outbox 补发。
9. 展示测试与压测原始报告。

演示前准备固定账号、场次和数据，避免现场临时修改数据库。所有管理操作使用 ADMIN 账号，不在代码中临时绕过权限。

## 12. 自检清单

- 能画出从请求到数据库的完整锁座时序。
- 能解释每个锁保护什么，以及最终裁决是谁。
- 能说明 Waiting Room 页面行为、参数和租约回收。
- 能说明订单 commit 与 Redis 回调的先后关系。
- 能说清生产者、Broker、消费者和幂等表。
- 能复述压测变量、数据和不能外推的结论。
- 能主动指出邀请码、JWT、H2、支付和可观测性边界。
- 能为每个简历关键词指出真实代码入口。

## 13. 配套资料

- [项目总览](01-project-overview.md)
- [业务与领域模型](02-business-and-domain.md)
- [缓存体系](04-cache-design.md)
- [Waiting Room](05-traffic-and-waiting-room.md)
- [锁座与支付](06-seat-order-payment.md)
- [Outbox 与 MQ](07-outbox-and-messaging.md)
- [测试与压测](11-testing-and-benchmark.md)
- [200 题面试清单](光影票务面试问题清单.md)
