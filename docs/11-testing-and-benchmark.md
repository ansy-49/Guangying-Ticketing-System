# 11. 测试与压测报告

## 1. 验证目标

项目测试不只验证“接口返回 200”，而是围绕三类证据：

1. 业务正确性：用户能否完成锁座、建单和支付。
2. 并发与可靠性：幂等、唯一约束、事务回调和 Outbox 抢占是否成立。
3. 性能趋势：缓存和库存更新方案是否在同环境 A/B 中改善目标指标。

## 2. 自动化测试矩阵

当前后端共 21 个测试用例，分布如下：

| 测试类 | 数量 | 覆盖重点 |
| --- | ---: | --- |
| `PurchaseFlowIntegrationTest` | 1 | 注册、登录、锁座、建单、支付主流程 |
| `MultiLevelCacheServiceTest` | 5 | 二次检查、锁超时回源、空值缓存、Pub/Sub、本地 single-flight |
| `ReliabilityHardeningIntegrationTest` | 5 | Outbox 抢占、DEAD 重放、事务回调、支付懒过期、超时事件幂等关单 |
| `OutboxTimerDispatchTest` | 1 | 超时 Outbox 使用绝对时间调用 RocketMQ 定时投递 API |
| `DesignPatternRegistryTest` | 4 | 限流策略路由、事件 Handler 路由、重复注册拒绝 |
| `LoginChainAndInterceptorTest` | 4 | Handler 顺序、重复 order、异常短路、Request ID 清理 |
| `SeatLayoutCacheIsolationTest` | 1 | 共享座位缓存不泄漏用户私有锁状态 |

### 2.1 后端执行

```powershell
cd backend
.\mvnw.cmd test
```

测试默认使用 H2 和降级配置，适合快速验证本地事务和应用逻辑。它不能替代真实 MySQL、Redis 和 RocketMQ 集成测试。

### 2.2 前端执行

```powershell
cd frontend
npm ci
npm run lint
npm run build
```

Build 同时验证 TypeScript 和 Next.js 生产构建。当前 `<img>` 性能建议类 lint warning 不影响构建，但可逐步迁移到 `next/image`。

### 2.3 CI

GitHub Actions 在 push 和 pull request 时分别执行：

- Java 17 + Maven 后端测试。
- Node.js 20 + `npm ci` + lint + build。

配置见 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)。

## 3. 缓存 A/B 压测

### 3.1 实验设计

测试接口：`GET /ajax/movieOnInfoList`

固定条件：

- 同一台机器、同一 JAR、同一 H2 数据集。
- JVM 堆 192~384 MB。
- 预热 100 次。
- 正式请求 3000 次，并发 100。
- 唯一自变量：`guangying.cache.enabled=false/true`。

### 3.2 结果

| 指标 | 关闭缓存 | 开启 Caffeine 热缓存 | 改善 |
| --- | ---: | ---: | ---: |
| 成功率 | 100% | 100% | 持平 |
| 吞吐 | 2470.32 RPS | 4287.08 RPS | +73.54% |
| 平均延迟 | 40.01 ms | 23.09 ms | -42.29% |
| P50 | 37.18 ms | 18.99 ms | -48.92% |
| P95 | 69.34 ms | 48.84 ms | -29.56% |
| P99 | 93.38 ms | 65.71 ms | -29.63% |

### 3.3 能证明什么

在该单实例、本地数据集和接口上，避免数据库重复查询明显改善吞吐和延迟，支持“热点电影列表适合本地缓存”这一技术判断。

### 3.4 不能证明什么

- 不能证明 Redis 二级缓存本身贡献了 73.5%，本次核心变量是缓存总开关且热路径主要命中 Caffeine。
- 不能证明线上集群容量为 4287 RPS。
- 不能覆盖缓存冷启动、热点 key 过期、Redis 网络延迟和数据库大数据量。
- 不能用该数据声称 RocketMQ 降低了接口多少毫秒。

## 4. 锁座并发对照

### 4.1 两个场景

1. 100 个用户争抢同一座位：验证最多一单成功。
2. 100 个用户选择不同座位：验证不应因场次级版本号产生无关冲突。

账号创建和登录属于准备阶段，不计入场景延迟和吞吐。

### 4.2 结果

| 指标 | 场次版本号乐观锁 | 条件原子扣减 |
| --- | ---: | ---: |
| 不同座位成功订单 | 6 / 100 | 100 / 100 |
| 有效下单吞吐 | 9.83 单/秒 | 96.42 单/秒 |
| 同座位成功订单 | 1 | 1 |

该结果说明旧方案把“场次行版本冲突”误当作“库存或座位冲突”。条件更新允许不同座位请求并发扣减库存，同时 Redis Lua 和唯一索引继续保证同座不超卖。

简历当前只保留缓存吞吐提升一条数据，是更稳妥的选择；锁座数据可以在面试追问时作为补充实验，而不必堆满简历。

## 5. 压测运行

### 5.1 隔离锁座环境

```powershell
docker compose -f load-tests/docker-compose.load.yml up -d --build
node load-tests/seat-race.mjs --base-url=http://localhost:18080 --users=100
docker compose -f load-tests/docker-compose.load.yml stop
```

该 Compose 使用临时数据存储，不复用项目正式卷，容器移除后数据消失。

### 5.2 查询 A/B

分别用缓存关闭和开启参数启动后端，然后执行：

```powershell
node load-tests/http-read.mjs --base-url=http://localhost:18082 --requests=3000 --concurrency=100 --warmup=100 --label=cache-on
```

结果写入 [`load-tests/results`](../load-tests/results)，同时保留 JSON 原始数据和 Markdown 摘要。

## 6. 性能报告规范

任何可写入简历或 README 的数字至少必须包含：

- 被测接口和代码版本。
- 硬件、JVM、数据库与中间件环境。
- 数据规模、预热次数、请求总量和并发度。
- 基线方案和实验方案的唯一变量。
- 成功率、吞吐、平均值、P95、P99。
- 原始结果文件和测试脚本。

只报告平均延迟会掩盖尾延迟，只报告吞吐会掩盖错误率；没有实验口径的百分比不具备可复现性。

## 7. RocketMQ 的正确验证方法

RocketMQ + Outbox 的主要收益是可靠性和解耦，不是当前压测已经证明的毫秒级加速。验证应围绕故障场景：

1. 停止 Broker。
2. 创建或支付订单，确认业务事务和 PENDING Outbox 成功提交。
3. 恢复 Broker。
4. 确认事件被发送、标记 SENT 并被消费者处理。
5. 人为模拟“发送成功但未标 SENT”，确认重复消息不重复执行副作用。
6. 让事件连续失败达到 DEAD，验证管理员重放。
7. 将支付截止时间缩短，验证 `ORDER_TIMEOUT_CHECK` 仅在到期后关闭一次，并写入 `DELAY_MESSAGE` 原因。
8. 停止消费者直到订单过期，验证恢复后的延迟消息或 DB 扫描均进入同一 CAS 关单链路且不重复返库存。

没有完成同步发送与异步 Outbox 的严格 A/B 前，不应写“RocketMQ 将接口降低 X ms”。

## 8. 尚缺的测试

| 缺口 | 建议 |
| --- | --- |
| 真实中间件集成 | Testcontainers 启动 MySQL、Redis、RocketMQ |
| 多实例竞争 | 同时运行两个应用实例验证 cache rebuild、queue reaper、Outbox claim |
| 故障注入 | Broker 重启、Redis 超时、数据库死锁、进程 kill |
| API 契约 | 为核心接口增加 MockMvc/OpenAPI 契约测试 |
| 前端交互 | Playwright 覆盖登录、排队、锁座、支付 |
| 长时间稳定性 | Soak test 观察连接、线程、锁和内存泄漏 |
| 安全性 | 越权、JWT 篡改、暴力登录、XSS 和依赖漏洞测试 |
| 数据迁移 | 空库和旧版本数据库分别执行迁移并跑回归 |

## 9. 发布门禁建议

```text
Maven test 通过
  -> Frontend lint/build 通过
  -> 数据库迁移验证
  -> 核心交易集成测试
  -> 中间件故障恢复测试
  -> 镜像与依赖安全扫描
  -> 小流量验证与回滚检查
```

校招项目不需要伪装成大型生产平台，但需要清楚说明“已经验证什么、尚未验证什么、下一步怎么验证”。这比单纯增加技术名词更有可信度。

## 10. 关键文件

- [`load-tests/README.md`](../load-tests/README.md)
- [`http-read.mjs`](../load-tests/http-read.mjs)
- [`seat-race.mjs`](../load-tests/seat-race.mjs)
- [`简历压测报告`](../load-tests/results/resume-benchmark.md)
- [`后端测试目录`](../backend/provider/src/test/java/com/guangying/provider)
- [`CI 配置`](../.github/workflows/ci.yml)

## 11. 面试追问索引

- 为什么缓存 A/B 只说明 Caffeine 热路径，而不能全归因于 Redis？
- 为什么必须预热，冷缓存又该怎么测？
- 不同座位成功率从 6% 到 100% 的根因是什么？
- 吞吐提升时如何确认不是错误率上升造成的？
- 为什么 H2 测试不能替代 MySQL 并发测试？
- 如何用故障实验验证 Outbox，而不是只跑 happy path？
