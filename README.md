# 光影票务 Movie Ticketing System

一个前后端分离的电影票务系统，重点实现电影浏览、影院/场次查询、选座锁座、下单、支付、想看计数，以及高并发抢票场景下的限流、防超卖和库存一致性方案。

> 本项目仅用于学习和面试展示。

## 项目亮点

- **选座下单链路**：座位图查询、同步锁座、创建待支付订单、支付成功后写入最终售出座位。
- **三层防超卖**：Redis Lua 原子锁座、MySQL 条件原子扣减、座位唯一索引兜底。
- **严格建单幂等**：客户端复用幂等键，Redisson 锁覆盖事务提交，MySQL `(user_id, idempotency_key)` 唯一索引最终兜底；请求指纹阻止同一键误用于不同场次或座位。
- **高并发保护**：AOP 令牌桶限流、热门场次排队令牌、座位锁自动过期与失败补偿。
- **缓存体系**：L1 Caffeine + L2 Redis 多级缓存；短 TTL 空值缓存拦截不存在 ID，热点 Key 回源使用 Redisson 看门狗锁与双重检查，随机 TTL 缓解集中失效，Redis Pub/Sub 广播多实例 L1 失效。
- **异步能力**：RocketMQ 承载订单领域事件，配合 Transactional Outbox、重试和消费幂等实现最终一致性。
- **可扩展设计**：限流采用策略/注册表模式，订单事件采用命令/注册表模式；登录使用责任链拆分校验步骤，请求通过 Context、日志、JWT 三级拦截链统一处理。
- **工程化部署**：支持本地 H2 快速启动，也支持 Docker Compose 启动 MySQL、Redis、RocketMQ、后端、前端、Nginx。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Next.js 14, React 18, TypeScript, Tailwind CSS, Zustand, Axios |
| 后端 | Spring Boot 3, Java 17, MyBatis-Plus, Maven 多模块 |
| 数据库 | H2 本地开发, MySQL 8 Docker/生产 |
| 缓存/锁 | Redis, Caffeine, Redisson |
| 消息队列 | RocketMQ |
| 部署 | Docker Compose, Nginx |

## 目录结构

```text
guangying-ticketing
├── backend/                 # Spring Boot 多模块后端
│   ├── common/              # 公共常量、工具、JWT
│   ├── domain/              # DTO/VO/PO/事件对象
│   ├── dao/                 # Mapper / XML
│   ├── service/             # 核心服务、缓存、限流、锁、MQ 消费者
│   ├── biz/                 # 业务编排层
│   └── provider/            # Controller、启动类、配置、SQL 初始化
├── frontend/                # Next.js 前端
├── docker/                  # MySQL / RocketMQ / Nginx / SSL 配置
├── load-tests/              # 并发锁座与热点读压测脚本
├── docker-compose.yml
├── .env.example
└── README.md
```

## 快速启动：本地开发

本地开发默认使用 H2 内存数据库，不依赖 MySQL、Redis、RocketMQ，适合先看功能。

### 1. 启动后端

```powershell
cd backend
.\mvnw.cmd -pl provider -am spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

H2 控制台：

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:guangying
User: sa
Password: 留空
```

### 2. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:3000
```

前端会通过 Next.js rewrites 把 `/ajax`、`/api`、`/dianying` 代理到 `http://localhost:8080`。

## Docker Compose 启动

Docker 模式会启动 MySQL、Redis、RocketMQ、后端、前端、Nginx。

如果复用的是早于“建单幂等键”版本创建的 MySQL 数据卷，请先备份数据库并执行
`docker/mysql/migrations/20261003_add_order_idempotency.sql`；全新数据卷会由初始化脚本直接创建最新表结构，无需执行迁移。

### 1. 准备环境变量

```powershell
Copy-Item .env.example .env
```

然后修改 `.env` 里的密码和 `JWT_SECRET`。`.env` 已经被 `.gitignore` 忽略，不要提交真实值。

### 2. 打包后端 JAR

当前后端 Dockerfile 使用本地预构建 JAR：

```powershell
cd backend
.\mvnw.cmd -q -DskipTests package
cd ..
```

### 3. 启动全栈

```powershell
docker compose up -d --build
```

默认 Nginx 使用 HTTP 初始化配置：

```text
http://localhost
```

如果要部署 HTTPS，请把 `docker/nginx/*.conf` 里的 `example.com` 改成自己的域名，并设置 `.env` 中的 `DEPLOY_DOMAIN`、`CERT_EMAIL`。

## 核心业务流程

```text
查询电影/场次
  -> 查询座位图
  -> 同步锁座，返回 lockToken
  -> 创建待支付订单，DB 条件原子扣减库存
  -> 支付成功，写入 order_seat，座位变为已售
  -> 超时未支付，定时任务取消订单并回滚库存
```

## 关键一致性设计

### 锁座

- 使用 Lua 一次性校验并锁定多个 `seat:lock:{scheduleId}:{row}_{col}` 座位 Key。
- 使用 `seat_lock(schedule_id, row_num, col_num)` 唯一索引兜底。
- 同一用户重试相同座位组合时直接返回原待支付订单；Lua 返回“本人已持有”时禁止重复建单，避免回滚误释放旧锁。
- 运行期 Redis 异常时锁座链路默认 fail-closed；仅无 Redis Bean 的本地演示模式使用数据库唯一索引兜底。
- 同一场次的座位锁、已售投影和 Waiting Room Key 统一使用 `{scheduleId}` Hash Tag，兼容 Redis Cluster 多 Key Lua。
- Redis 锁座成功后在同一数据库事务内扣减库存、写入座位锁和待支付订单。

### 下单防超卖

- 前端为一次购票意图生成幂等键，网络重试复用原值；服务端保存 SHA-256 请求指纹，幂等键与请求内容不一致时直接拒绝。
- Redisson 按“用户 + 幂等键”串行化同一请求并覆盖数据库 commit；Redis 降级或锁租约极端到期时，MySQL 唯一索引仍保证最多生成一笔订单。
- MySQL 使用带 `available_seats >= seatCount` 条件的单条 UPDATE 原子校验并扣减库存。
- `movie_schedule.version` 随每次库存变更递增，用于审计库存变更次数，不参与拒绝无关座位订单。
- 创建待支付订单后，`seat_lock` 绑定 `order_no`。
- 不提供脱离订单的独立解锁接口；座位释放统一由订单取消事务完成状态迁移、库存归还和 Outbox 落库。
- 超时扫描只负责发现订单，每个订单使用独立 `REQUIRES_NEW` 事务关闭，单笔失败不影响整批。
- 订单超时或取消时回滚数据库库存并释放 Redis 座位锁。

### 消息可靠性

- 消费端先通过 `processed_event.event_id` 唯一键原子抢占，再在同一事务内执行处理器；处理失败时幂等记录一起回滚。
- 未知事件类型不会被误标记为已处理，而是抛出异常交给 RocketMQ 重试与死信机制。

### 缓存一致性

- 场次列表：L1 Caffeine -> L2 Redis -> Redisson Key 级重建锁 -> 二次检查 -> DB。
- 电影详情不存在时写入 60 秒空值标记，避免恶意或重复无效 ID 持续穿透至数据库。
- 只有获得重建锁的实例能够回源；锁等待超时或 Redisson 异常时复查缓存，仍为空则降级访问 DB，优先保证可用性。
- 写操作采用 Cache Aside 删除 L2 后，通过 Redis Pub/Sub 广播失效消息，各实例只清理自己的 Caffeine；广播异常时由 L1 的 60 秒 TTL 兜底收敛。
- 已售座位在 Redis 中维护加速投影，数据库订单与座位表作为最终数据源。
- 交易链路不依赖展示缓存，下单时重新执行 Redis Lua 锁座与数据库条件扣减。

### 想看计数

- 想看用户集合与计数 Key 使用相同 `{movieId}` Hash Tag，由 Lua 原子完成用户去重、数据库基线初始化和计数递增，兼容 Redis Cluster。
- RocketMQ 重复投递时先由 `user_wish(user_id, movie_id)` 唯一索引判重，只有首次插入成功才更新电影计数，避免至少一次投递造成重复累加。
- 数据库写回成功后统一删除电影详情、热映、待映和最受期待缓存，并广播 L1 失效。

## 常用命令

```powershell
# 后端编译
cd backend
.\mvnw.cmd -q -DskipTests compile

# 后端打包
.\mvnw.cmd -q -DskipTests package

# 前端构建
cd ..\frontend
npm run build

# Docker 全栈启动
cd ..
docker compose up -d --build
```

## License

仅用于学习、课程设计、面试展示。正式商用前请自行补充许可证、合规声明和安全审计。
