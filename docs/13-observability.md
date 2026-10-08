# 13. 可观测性与运维

## 1. 当前实现与目标状态

当前已有：

- `X-Request-Id` 生成、透传和 MDC 上下文。
- HTTP 方法、URI、状态码、耗时日志。
- 超过 1000 ms 的慢请求日志。
- 缓存命中/回源、排队回收、锁座、支付、Outbox 和消费者日志。
- Outbox `last_error`、重试次数与 DEAD 管理接口。

当前没有接入 Spring Boot Actuator、Micrometer、Prometheus、Grafana 或分布式追踪。下文的指标、面板和告警属于生产化建议，不应描述为已上线能力。

## 2. 可观测性目标

系统必须能回答：

1. 用户请求在哪里变慢或失败？
2. 热门场次被限流、排队、锁座和支付的转化漏斗是什么？
3. MySQL、Redis、RocketMQ 哪个组件成为瓶颈？
4. Outbox 和消费者是否正在积压？
5. 数据库权威数据与 Redis 投影是否发生偏差？

## 3. 日志规范

建议所有结构化日志包含：

| 字段 | 示例 | 用途 |
| --- | --- | --- |
| `timestamp` | ISO-8601 + 时区 | 跨组件对时 |
| `level` | INFO/WARN/ERROR | 筛选严重程度 |
| `requestId` | 32 位 UUID | 关联一次 HTTP 请求 |
| `userId` | 123 | 定位用户链路，注意脱敏 |
| `orderNo` | GY... | 关联交易 |
| `scheduleId` | 88 | 关联热门场次 |
| `eventId` | UUID | 关联 Outbox 和消费 |
| `action` | seat.lock | 稳定的操作名 |
| `result` | success/conflict | 统计结果分布 |
| `durationMs` | 37 | 性能分析 |

密码、JWT、数据库密码、完整邀请码和支付秘密绝不能写日志。异常返回给用户时不暴露堆栈，详细堆栈只留服务端。

## 4. RED 与 USE 指标

### 4.1 HTTP RED

- Rate：按接口和状态码的请求速率。
- Errors：5xx、业务拒绝、限流、队列满、座位冲突比例。
- Duration：平均、P50、P95、P99。

业务拒绝不能全部算系统错误。例如“座位已被别人锁定”是预期竞争结果，但比例突增仍可能说明热点或前端刷新不及时。

### 4.2 资源 USE

- Utilization：CPU、堆、GC、数据库/Redis 连接池占用。
- Saturation：线程池队列、连接等待、RocketMQ backlog、Outbox PENDING。
- Errors：连接超时、死锁、OOM、Redis/MQ 调用异常。

## 5. 业务漏斗指标

```text
场次详情访问
  -> 请求进入 Waiting Room
  -> 获得入场令牌
  -> Lua 锁座成功
  -> PENDING 订单创建
  -> PAID 支付成功
```

建议按 `scheduleId` 统计：

- queue_enter_total / queue_admitted_total / queue_full_total。
- queue_waiting_current / queue_lease_current / lease_expired_total。
- seat_lock_acquired/conflict/unavailable。
- order_created/paid/cancelled/timeout。
- payment_insufficient_points / payment_state_conflict。
- 从准入到建单、从建单到支付的转化率。

漏斗能区分“流量很大但都在排队”和“已进入交易却大量锁座失败”等不同问题。

## 6. 缓存指标

- L1 hit、L1 null hit、L2 hit、DB load。
- 命中率按缓存名称而不是全局混合统计。
- cache rebuild lock wait、timeout 和持锁耗时。
- Redis read/write/evict/pubsub error。
- loader 延迟和并发回源数。
- Caffeine size、eviction count。

命中率高不一定健康：如果缓存旧值长期不失效，命中率会很高但数据错误，因此还需监控失效消息和数据新鲜度。

## 7. 交易指标

- 锁座请求速率、成功率、冲突率、P95/P99。
- 数据库库存条件更新失败数。
- seat_lock/order_seat 唯一键冲突数。
- 幂等重放命中数、请求指纹冲突数。
- 事务回滚和 Redis 补偿执行/失败数。
- 定时消息消费延迟、重复命中数、关单成功数，以及 DB 兜底扫描命中量、单批耗时和最老积压年龄。

高冲突率可能是正常热门，也可能是 Redis 锁失效、座位图缓存过旧或机器人请求，需要结合场次和错误分布判断。

## 8. Outbox 与 MQ 指标

最重要的不是只有“发送成功数”，而是状态与年龄：

- outbox_pending_current。
- oldest_pending_age_seconds。
- outbox_processing_expired_total。
- outbox_retry_total / dead_current。
- send_latency、send_error 按异常类型。
- consumer_lag、consume_latency、consume_error。
- processed_event_duplicate_total。
- Handler 按事件类型的耗时和失败数。

建议告警示例：

| 条件 | 严重度 | 含义 |
| --- | --- | --- |
| 最老 PENDING > 60 秒持续 5 分钟 | P2 | 消息投递明显落后 |
| DEAD > 0 | P2 | 有事件需要人工处理 |
| PROCESSING 过期持续增长 | P2 | 发送线程卡住或租约过短 |
| 消费失败率 > 5% | P2 | Handler 或下游异常 |

阈值必须通过基线调整，表中只提供初始示例。

## 9. 数据一致性对账

定期检查：

- PAID 订单的 seat_count 是否等于 order_seat 条数。
- 同一场次是否存在重复 order_seat 行列。
- available_seats 与已售、有效待支付占用是否匹配。
- PENDING 订单是否都存在有效 seat_lock。
- CANCELLED 订单是否仍残留锁定中记录。
- Redis 已售集合是否与 order_seat 抽样一致。
- SENT Outbox 是否存在长期未被 processed_event 记录的异常情况。

对账发现问题后先输出差异报告，再通过有审计的修复任务处理，避免自动覆盖掩盖根因。

## 10. Trace 设计

未来引入 OpenTelemetry 时，建议关键 Span：

```text
HTTP lockSeats
  -> rateLimit.redis.lua
  -> queue.validate
  -> idempotency.redisson.lock
  -> seat.redis.lua
  -> db.purchase.transaction
      -> schedule.deduct
      -> seat_lock.insert
      -> order.insert
      -> outbox.insert

outbox.poll
  -> outbox.claim
  -> rocketmq.send

rocketmq.consume
  -> processed_event.insert
  -> handler.execute
```

异步链路需要把 `eventId` 和追踪上下文写入消息 envelope，才能跨线程、跨进程关联。

## 11. 面板设计

### 11.1 总览面板

- 请求速率、错误率、P95/P99。
- 锁座/支付成功率。
- JVM、数据库连接池、Redis 和 RocketMQ 健康。
- Outbox 最老事件年龄和 DEAD 数。

### 11.2 热门场次面板

- 每场次 waiting、lease、maxAdmission。
- 准入速率、平均等待、过期租约。
- 座位冲突、建单和支付转化。
- 库存与座位对账差异。

### 11.3 消息面板

- 事件类型吞吐。
- PENDING/PROCESSING/SENT/DEAD。
- 发送和消费延迟。
- 重复消费与 Handler 失败。

## 12. 落地优先级

1. 引入 Actuator + Micrometer，暴露 JVM、HTTP、Hikari 基础指标。
2. 为缓存、Waiting Room、锁座和 Outbox增加业务 Counter/Gauge/Timer。
3. Prometheus 抓取，Grafana 建三张核心面板。
4. 对最老 Outbox、DEAD、支付错误率和数据库连接池设置告警。
5. 日志改为 JSON 并集中采集。
6. 引入 OpenTelemetry 串联 HTTP、DB、Redis 和 MQ。
7. 建立定期一致性对账任务。

## 13. 当前代码入口

- [`RequestContextInterceptor.java`](../backend/provider/src/main/java/com/guangying/provider/interceptor/RequestContextInterceptor.java)
- [`RequestLogInterceptor.java`](../backend/provider/src/main/java/com/guangying/provider/interceptor/RequestLogInterceptor.java)
- [`GlobalExceptionHandler.java`](../backend/provider/src/main/java/com/guangying/provider/advice/GlobalExceptionHandler.java)
- [`OutboxAdminController.java`](../backend/provider/src/main/java/com/guangying/provider/controller/OutboxAdminController.java)
