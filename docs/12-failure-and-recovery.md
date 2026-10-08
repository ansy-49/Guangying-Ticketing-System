# 12. 故障场景与恢复手册

## 1. 恢复原则

系统的恢复顺序遵循三个原则：

1. **先确认权威数据**：订单、库存、积分、成交座位和事件状态以 MySQL 为准。
2. **再恢复可重建状态**：缓存、已售投影、排队令牌和临时座位锁可以删除、过期或从数据库重建。
3. **最后恢复异步进度**：Outbox、MQ 消费和投影更新允许短暂延迟，但不能无声丢失。

任何人工修复都应先备份、记录请求 ID/订单号/事件 ID，再使用幂等接口或条件 SQL，避免把一次故障扩大成二次写错。

## 2. 故障分级

| 级别 | 示例 | 用户影响 | 处理目标 |
| --- | --- | --- | --- |
| P0 | 库存重复成交、积分错误扣减 | 交易正确性受损 | 立即停止相关写入口，保护证据并修复 |
| P1 | 数据库不可用、全站锁座失败 | 核心交易不可用 | 快速止损、恢复权威存储 |
| P2 | Redis/MQ 故障、排队或异步投影延迟 | 部分能力降级 | 限制流量，依靠 DB/Outbox 恢复 |
| P3 | 单 key 缓存异常、个别 DEAD 事件 | 局部体验问题 | 定位、重建或人工重放 |

## 3. Redis 故障

### 3.1 查询缓存

**表现**：L2 读取/写入错误日志增多，数据库 QPS 上升。

**当前行为**：

- L1 命中仍可返回。
- L2 失败后进入本地 single-flight 并回源 MySQL。
- Redisson 不可用时跨实例互斥消失，但单实例仍合并请求。

**风险**：所有实例同时回源可能形成缓存击穿放大。

**处置**：限制非核心查询流量，观察数据库连接池和慢查询；Redis 恢复后让缓存自然回填，必要时预热高频 key。

### 3.2 Waiting Room

**表现**：热门场次进入或状态查询返回暂时不可用。

**当前行为**：运行期进入失败抛错，令牌校验失败拒绝锁座；本地无 Redis 模式才明确直通。

**处置**：不要临时把热门场次全部伪造成普通场次。优先暂停热门场次写入口或降低上游流量，恢复 Redis 后重新初始化热点配置并核对 waiting/lease/token。

### 3.3 座位锁

**表现**：Lua 返回 UNAVAILABLE，锁座失败。

**当前行为**：完整环境 fail-closed，不把压力直接推向数据库。

**处置**：恢复 Redis；检查是否发生淘汰。当前演示 Compose 使用 `allkeys-lru`，内存紧张时临时座位锁和排队 key 也可能被淘汰，生产应为关键租约使用不易被淘汰的独立实例/命名空间或选择更安全的内存策略。

### 3.4 Redis 数据丢失

- 电影与影院缓存：从 MySQL 回源。
- 已售座位投影：`SeatSoldService` 从 `order_seat` 重建。
- 临时座位锁：无法从 Redis 完全恢复剩余 TTL，但数据库 `seat_lock` 仍可用于校验；应短暂收紧锁座入口并运行对账。
- Waiting Room：属于短期调度状态，可重新初始化热门场次，但用户需要重新排队。

## 4. MySQL 故障

### 4.1 数据库完全不可用

查询可能由已有缓存短暂承接，但注册、锁座、支付、取消、Outbox 和权限检查都会失败。不能把缓存数据升级为交易权威继续售票。

处置顺序：

1. 暂停交易写入口，避免客户端持续重试形成雪崩。
2. 检查连接池耗尽、网络、磁盘、主库状态和慢事务。
3. 恢复数据库或切换经过验证的主节点。
4. 检查未完成事务已回滚。
5. 扫描 PENDING 订单、Outbox、库存与座位一致性。
6. 逐步恢复流量。

### 4.2 死锁或事务超时

事务会回滚；锁座路径的 afterRollback 尝试释放 Redis 预占。客户端使用同一幂等键重试，可以获取已存在订单或重新发起事务。

需要记录死锁 SQL、索引和加锁顺序，不能只在应用层无限重试。重试应有上限和随机退避。

### 4.3 库存与座位不一致

对账关系：

```text
场次已售数 = order_seat 中该场次的有效记录数
场次待支付占用 = status=1 的有效 seat_lock 数
理论 available = total_seats - 已售数 - 待支付占用
```

当前 `available_seats` 在建单时已经扣除待支付座位，取消/超时返还。发现偏差时先停止该场次写入，按订单和锁记录查明来源，再使用审计化脚本修正，不能直接把某一侧覆盖另一侧。

## 5. 应用实例崩溃

### 5.1 Redis 锁成功、数据库提交前崩溃

数据库事务回滚或未发生，事务回调可能来不及执行。Redis 座位锁最多保留 15 分钟后自动到期，Waiting Room 入场租约最多保留 10 分钟并由回收任务清理。

### 5.2 数据库建单已提交、响应前崩溃

客户端不知道是否成功。重试时复用相同幂等键，服务查询 `(user_id, idempotency_key)` 并返回原订单。不得生成新幂等键盲目重试。

### 5.3 支付已提交、Redis 更新前崩溃

MySQL 已保存 PAID 和 `order_seat`。ORDER_PAID Outbox 会被新实例发送，消费者从数据库重建已售投影；座位图读取在 Redis 为空时也会回源 DB。

### 5.4 Outbox 发送后、标记 SENT 前崩溃

发送租约到期后事件再次发送。消费者的 `processed_event(event_id)` 唯一键阻止重复副作用。

## 6. RocketMQ 故障

### 6.1 Broker 不可用

业务事务不依赖 Broker 在线，订单和 Outbox 可以提交。发送失败的事件按指数退避回到 PENDING，达到上限进入 DEAD。

处置：

1. 检查 NameServer、Broker、磁盘和网络。
2. 观察 PENDING 数量和最老事件年龄。
3. 恢复 Broker 后确认 backlog 持续下降。
4. 检查 DEAD，修复根因后由管理员显式重放。

### 6.2 消费者持续失败

消息由 Broker 重投。检查异常是否为未知事件类型、载荷不兼容、数据库故障或处理器逻辑错误。不要通过直接插入 `processed_event` 跳过消息，这会掩盖未完成副作用。

### 6.3 消息积压

优先看最老消息延迟、消费失败率和处理器耗时。只有处理器幂等且下游容量足够时才能增加消费并发；盲目扩容可能把压力转移到 MySQL/Redis。

## 7. Outbox 故障

| 状态 | 诊断重点 | 操作 |
| --- | --- | --- |
| PENDING 长时间不动 | 调度器是否运行、next_retry_time、MQ 连接 | 恢复发送链路 |
| PROCESSING 超过租约 | 实例崩溃或发送卡住 | 等租约回收，检查发送 P99 |
| DEAD 增长 | 持续配置/载荷/代码错误 | 查看 last_error，修复后单条重放 |
| SENT 但未见投影 | 消费组、消费异常、processed_event | 检查 Broker 和消费者日志 |

人工重放只允许 DEAD -> PENDING，避免把正常 SENT 事件随意再次投入队列。

## 8. Waiting Room 异常

### 8.1 入场数不下降

检查 lease ZSet 中是否存在到期 score、reaper 是否每 5 秒运行、hot schedule 集合是否包含场次。token 自动过期并不会自动触发应用逻辑，必须由 lease reaper 回收。

### 8.2 重复 leave

Lua 只有实际删除租约时才晋升下一位，重复调用不会重复释放容量。检查日志中的 `released` 值。

### 8.3 用户一直显示排队

检查 waiting rank、lease 容量、maxAdmission 是否过小、前端轮询和 token key。预计等待只是固定 30 秒/人的启发式，不能用于判断系统卡死。

## 9. 超时订单异常

正常路径由 `ORDER_TIMEOUT_CHECK` 定时消息到点触发。若过期订单增长：

- 检查专用 Topic 是否为 DELAY 类型、Broker TimerMessageStore、消费组与消息积压。
- 对比应用、数据库和 Broker 时钟，排除消息提前/延后。
- 检查 Outbox 中超时事件是否停在 PENDING/PROCESSING/DEAD。
- 检查兜底扫描调度器是否运行。
- 检查 `expire_time/status` 查询索引和 SQL。
- 检查单笔关闭是否因库存返还或 Outbox 写入失败。
- 关注每批 100、每轮 10 批是否追不上积压速度。

定时消息、支付懒过期和扫描都调用统一 CAS 关单；重复触发只允许一个事务从 `PENDING` 迁移并返还库存。扫描中每笔订单使用独立事务，因此单笔异常不阻塞同批其他订单。持续出现 `DB_SCAN_FALLBACK` 不是正常流量，应作为延时链路健康告警。

## 10. 人工恢复禁区

- 不直接删除 PAID 订单来“释放座位”。
- 不在未核对订单时批量清空 `seat_lock`。
- 不把 Redis 可售数直接覆盖 MySQL 库存。
- 不为消除积压而把 Outbox PENDING 批量改成 SENT。
- 不为跳过消费错误而伪造 `processed_event`。
- 不在无备份情况下删除 Docker 数据卷。

## 11. 恢复后验收

1. 核心 API 成功率和 P95 恢复。
2. 数据库库存、锁座和成交座位对账一致。
3. Waiting Room waiting/lease 数量合理，过期租约被回收。
4. Outbox 最老 PENDING 年龄持续下降，DEAD 无异常增长。
5. RocketMQ 消费延迟恢复且重复消息被幂等跳过。
6. Redis 已售投影与数据库抽样一致。
7. 记录故障时间线、根因、影响、恢复动作和预防项。

## 12. 关键代码

- [`TransactionCallbacks.java`](../backend/service/src/main/java/com/guangying/service/infrastructure/TransactionCallbacks.java)
- [`QueueService.java`](../backend/service/src/main/java/com/guangying/service/infrastructure/QueueService.java)
- [`OutboxService.java`](../backend/service/src/main/java/com/guangying/service/infrastructure/OutboxService.java)
- [`OrderService.java`](../backend/service/src/main/java/com/guangying/service/OrderService.java)
- [`SeatSoldService.java`](../backend/service/src/main/java/com/guangying/service/infrastructure/SeatSoldService.java)
