# 光影票务 Movie Ticketing System

一个前后端分离的电影票务系统，重点实现电影浏览、影院/场次查询、选座锁座、下单、支付、想看计数，以及高并发抢票场景下的限流、防超卖和库存一致性方案。

> 本项目仅用于学习和面试展示。

## 项目亮点

- **选座下单链路**：座位图查询、同步锁座、创建待支付订单、支付成功后写入最终售出座位。
- **三层防超卖**：Redis Lua 原子锁座、MySQL 条件原子扣减、座位唯一索引兜底。
- **高并发保护**：AOP 令牌桶限流、热门场次排队令牌、座位锁自动过期与失败补偿。
- **缓存体系**：L1 Caffeine + L2 Redis 多级缓存；短 TTL 空值缓存拦截不存在 ID，热点 Key 回源使用 Redisson 看门狗锁与双重检查，并以随机 TTL 缓解集中失效。
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
- Redis 不可用时降级到数据库唯一索引保证正确性。
- Redis 锁座成功后在同一数据库事务内扣减库存、写入座位锁和待支付订单。

### 下单防超卖

- MySQL 使用带 `available_seats >= seatCount` 条件的单条 UPDATE 原子校验并扣减库存。
- `movie_schedule.version` 随每次库存变更递增，用于审计库存变更次数，不参与拒绝无关座位订单。
- 创建待支付订单后，`seat_lock` 绑定 `order_no`。
- 订单超时或取消时回滚数据库库存并释放 Redis 座位锁。

### 缓存一致性

- 场次列表：L1 Caffeine -> L2 Redis -> Redisson Key 级重建锁 -> 二次检查 -> DB。
- 电影详情不存在时写入 60 秒空值标记，避免恶意或重复无效 ID 持续穿透至数据库。
- 只有获得重建锁的实例能够回源；锁等待超时或 Redisson 异常时复查缓存，仍为空则降级访问 DB，优先保证可用性。
- 已售座位在 Redis 中维护加速投影，数据库订单与座位表作为最终数据源。
- 交易链路不依赖展示缓存，下单时重新执行 Redis Lua 锁座与数据库条件扣减。

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
