-- =====================================================
-- 光影后端 - 数据库表结构 (H2 MySQL 兼容模式)
-- =====================================================

-- 电影表
CREATE TABLE IF NOT EXISTS movie (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nm              VARCHAR(200)  NOT NULL        COMMENT '电影名',
    enm             VARCHAR(200)                  COMMENT '英文名',
    img             VARCHAR(500)                  COMMENT '海报URL',
    sc              DECIMAL(3,1)  DEFAULT 0       COMMENT '评分',
    star            VARCHAR(500)                  COMMENT '演员',
    cat             VARCHAR(200)                  COMMENT '分类标签',
    src             VARCHAR(200)                  COMMENT '来源/国家',
    dur             INT                           COMMENT '时长(分钟)',
    pub_desc        VARCHAR(200)                  COMMENT '上映描述',
    dra             TEXT                          COMMENT '剧情简介',
    wish            INT           DEFAULT 0       COMMENT '想看人数',
    vd              VARCHAR(500)                  COMMENT '预告片URL',
    photos          TEXT                          COMMENT '剧照JSON数组',
    pn              INT           DEFAULT 0       COMMENT '剧照总数',
    show_info       VARCHAR(200)                  COMMENT '上映信息',
    coming_title    VARCHAR(100)                  COMMENT '即将上映标题',
    movie_status    INT           DEFAULT 0       COMMENT '0=即将上映,1=正在热映,2=已下映',
    global_released INT           DEFAULT 0       COMMENT '是否已上映:0=否,1=是',
    release_year    INT           DEFAULT 2026    COMMENT '上映年份',
    sort_order      INT           DEFAULT 0       COMMENT '排序权重',
    version         INT           DEFAULT 0       COMMENT '乐观锁版本号',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted         INT           DEFAULT 0
);

-- 城市表
CREATE TABLE IF NOT EXISTS city (
    id  BIGINT PRIMARY KEY               COMMENT '城市ID',
    nm  VARCHAR(50)  NOT NULL             COMMENT '城市名',
    py  VARCHAR(100)                      COMMENT '拼音'
);

-- 影院表
CREATE TABLE IF NOT EXISTS cinema (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    nm                  VARCHAR(200) NOT NULL  COMMENT '影院名称',
    addr                VARCHAR(500)           COMMENT '地址',
    city_id             BIGINT       NOT NULL  COMMENT '城市ID',
    brand_id            BIGINT                 COMMENT '品牌ID',
    district_id         BIGINT                 COMMENT '行政区ID',
    area_id             BIGINT                 COMMENT '商圈ID',
    distance            VARCHAR(50)            COMMENT '距离描述',
    allow_refund        INT DEFAULT 0          COMMENT '可退票',
    endorse             INT DEFAULT 0          COMMENT '可改签',
    snack               INT DEFAULT 0          COMMENT '有小吃',
    vip_tag             VARCHAR(100)           COMMENT 'VIP标签',
    hall_types_json     VARCHAR(500)           COMMENT '厅型JSON数组',
    card_promotion_tag  VARCHAR(200)           COMMENT '促销标签',
    sort_order          INT DEFAULT 0          COMMENT '排序权重',
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             INT DEFAULT 0
);

-- 影院品牌表
CREATE TABLE IF NOT EXISTS cinema_brand (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    count INT DEFAULT 0
);

-- 行政区/商圈表
CREATE TABLE IF NOT EXISTS district (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    city_id   BIGINT  NOT NULL,
    parent_id BIGINT  DEFAULT 0   COMMENT '0=顶级行政区',
    count     INT     DEFAULT 0
);

-- 地铁线路/站点表
CREATE TABLE IF NOT EXISTS subway (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    city_id   BIGINT  NOT NULL,
    parent_id BIGINT  DEFAULT 0   COMMENT '0=地铁线,>0=站点',
    count     INT     DEFAULT 0
);

-- 服务类型表
CREATE TABLE IF NOT EXISTS service_type (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    count INT DEFAULT 0
);

-- 影厅类型表
CREATE TABLE IF NOT EXISTS hall_type (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    count INT DEFAULT 0
);

-- 影院-服务关联表
CREATE TABLE IF NOT EXISTS cinema_service_rel (
    cinema_id  BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    PRIMARY KEY (cinema_id, service_id)
);

-- 影院-厅型关联表
CREATE TABLE IF NOT EXISTS cinema_hall_type_rel (
    cinema_id    BIGINT NOT NULL,
    hall_type_id BIGINT NOT NULL,
    PRIMARY KEY (cinema_id, hall_type_id)
);

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account        VARCHAR(100) NOT NULL UNIQUE,
    password       VARCHAR(200) NOT NULL,
    user_nick      VARCHAR(100),
    user_head_img  VARCHAR(500),
    points         INT DEFAULT 0        COMMENT '用户积分(1积分=1元)',
    role           VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        INT DEFAULT 0
);

-- 场次表（防超卖核心场景）
CREATE TABLE IF NOT EXISTS movie_schedule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id        BIGINT        NOT NULL      COMMENT '关联电影',
    cinema_id       BIGINT        NOT NULL      COMMENT '关联影院',
    hall_name       VARCHAR(50)                 COMMENT '影厅名称',
    show_date       DATE          NOT NULL      COMMENT '放映日期',
    show_time       VARCHAR(10)   NOT NULL      COMMENT '放映时间 HH:mm',
    end_time        VARCHAR(10)                 COMMENT '散场时间 HH:mm',
    lang            VARCHAR(30)   DEFAULT '国语' COMMENT '语言版本',
    total_seats     INT           NOT NULL DEFAULT 120 COMMENT '总座位数',
    available_seats INT           NOT NULL DEFAULT 120 COMMENT '剩余可售座位',
    price           DECIMAL(10,2) NOT NULL DEFAULT 39.90 COMMENT '单价',
    status          INT           DEFAULT 1     COMMENT '1=可售 0=停售',
    version         INT           DEFAULT 0     COMMENT '乐观锁版本号',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted         INT           DEFAULT 0
);

-- 订单表
CREATE TABLE IF NOT EXISTS ticket_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(64)   NOT NULL UNIQUE COMMENT '订单编号',
    user_id         BIGINT        NOT NULL      COMMENT '用户ID',
    idempotency_key VARCHAR(64)    NOT NULL      COMMENT '客户端建单幂等键',
    request_fingerprint VARCHAR(64) NOT NULL     COMMENT '场次与座位请求摘要',
    schedule_id     BIGINT        NOT NULL      COMMENT '场次ID',
    lock_token      VARCHAR(64)                 COMMENT '锁座令牌',
    movie_name      VARCHAR(200)                COMMENT '电影名（冗余）',
    cinema_name     VARCHAR(200)                COMMENT '影院名（冗余）',
    hall_name       VARCHAR(50)                 COMMENT '厅名（冗余）',
    show_time       VARCHAR(30)                 COMMENT '放映时间（冗余）',
    seat_count      INT           NOT NULL DEFAULT 1 COMMENT '座位数',
    seats_info      VARCHAR(500)                COMMENT '座位信息(如:5排3座,5排4座)',
    unit_price      DECIMAL(10,2)               COMMENT '单价',
    total_price     DECIMAL(10,2)               COMMENT '总价',
    status          INT           DEFAULT 0     COMMENT '0=待支付 1=已支付 2=已取消 3=已退款',
    expire_time     TIMESTAMP     NULL          COMMENT '支付截止时间',
    pay_time        TIMESTAMP     NULL          COMMENT '支付时间',
    cancel_time     TIMESTAMP     NULL          COMMENT '取消时间',
    cancel_reason   VARCHAR(32)   NULL          COMMENT '取消原因',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted         INT           DEFAULT 0
);

-- 用户想看记录表
CREATE TABLE IF NOT EXISTS user_wish (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL      COMMENT '用户ID',
    movie_id        BIGINT        NOT NULL      COMMENT '电影ID',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- 影院影厅座位布局表（物理座位图：行列+过道+情侣座）
CREATE TABLE IF NOT EXISTS cinema_hall (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    cinema_id       BIGINT        NOT NULL      COMMENT '关联影院',
    hall_name       VARCHAR(50)   NOT NULL      COMMENT '影厅名称',
    seat_rows       INT           NOT NULL DEFAULT 10  COMMENT '座位行数',
    seat_cols       INT           NOT NULL DEFAULT 14  COMMENT '座位列数',
    aisle_after_col VARCHAR(50)   DEFAULT ''            COMMENT '过道位于第N列之后,逗号分隔',
    couple_rows     VARCHAR(50)   DEFAULT ''            COMMENT '情侣座行号,逗号分隔',
    disabled_seats  TEXT                                COMMENT '不可用座位JSON [[row,col],...]',
    hall_type       VARCHAR(50)   DEFAULT '普通厅'       COMMENT '厅类型',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted         INT           DEFAULT 0
);

-- 座位锁定表（高并发锁座核心表 — 悲观锁/分布式锁配合使用）
CREATE TABLE IF NOT EXISTS seat_lock (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id     BIGINT        NOT NULL      COMMENT '场次ID',
    row_num         INT           NOT NULL      COMMENT '行号',
    col_num         INT           NOT NULL      COMMENT '列号',
    user_id         BIGINT        NOT NULL      COMMENT '锁座用户ID',
    lock_token      VARCHAR(64)   NOT NULL      COMMENT '锁座批次令牌',
    order_no        VARCHAR(64)   NULL          COMMENT '绑定的订单号',
    lock_until      TIMESTAMP     NOT NULL      COMMENT '锁定到期时间',
    status          INT           DEFAULT 1     COMMENT '1=锁定中 0=已释放 2=已购买',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- 订单座位明细表（一对多：一个订单对应多个座位）
CREATE TABLE IF NOT EXISTS order_seat (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT        NOT NULL      COMMENT '订单ID',
    order_no        VARCHAR(64)   NOT NULL      COMMENT '订单编号',
    schedule_id     BIGINT        NOT NULL      COMMENT '场次ID',
    row_num         INT           NOT NULL      COMMENT '行号',
    col_num         INT           NOT NULL      COMMENT '列号',
    seat_label      VARCHAR(20)   NOT NULL      COMMENT '座位标签(如5排3座)',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 索引 ====================
CREATE INDEX IF NOT EXISTS idx_movie_status ON movie(movie_status, deleted, sort_order);
CREATE INDEX IF NOT EXISTS idx_movie_wish   ON movie(wish DESC);
CREATE INDEX IF NOT EXISTS idx_cinema_city  ON cinema(city_id, deleted, sort_order);
CREATE INDEX IF NOT EXISTS idx_cinema_brand ON cinema(brand_id);
CREATE INDEX IF NOT EXISTS idx_district_city ON district(city_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_subway_city  ON subway(city_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_user_account ON sys_user(account);
CREATE INDEX IF NOT EXISTS idx_schedule_movie ON movie_schedule(movie_id, show_date, deleted);
CREATE INDEX IF NOT EXISTS idx_schedule_cinema ON movie_schedule(cinema_id, show_date, deleted);
CREATE INDEX IF NOT EXISTS idx_order_user   ON ticket_order(user_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_order_no     ON ticket_order(order_no);
CREATE INDEX IF NOT EXISTS idx_order_expire ON ticket_order(status, deleted, expire_time, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_order_idempotency ON ticket_order(user_id, idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_wish_unique ON user_wish(user_id, movie_id);
CREATE INDEX IF NOT EXISTS idx_hall_cinema ON cinema_hall(cinema_id, deleted);
CREATE UNIQUE INDEX IF NOT EXISTS idx_hall_unique ON cinema_hall(cinema_id, hall_name, deleted);
CREATE INDEX IF NOT EXISTS idx_seat_lock_schedule ON seat_lock(schedule_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_seat_lock_unique ON seat_lock(schedule_id, row_num, col_num);
CREATE INDEX IF NOT EXISTS idx_seat_lock_user ON seat_lock(user_id, status);
CREATE INDEX IF NOT EXISTS idx_seat_lock_token ON seat_lock(lock_token);
CREATE INDEX IF NOT EXISTS idx_seat_lock_expire ON seat_lock(lock_until, status);
CREATE INDEX IF NOT EXISTS idx_order_seat_order ON order_seat(order_id);
CREATE INDEX IF NOT EXISTS idx_order_seat_schedule ON order_seat(schedule_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_order_seat_unique ON order_seat(schedule_id, row_num, col_num);
CREATE INDEX IF NOT EXISTS idx_movie_year ON movie(release_year, movie_status, deleted);

-- Outbox 事件表（本地事务消息 — 保证 DB 改了 ⟺ 事件最终发出）
CREATE TABLE IF NOT EXISTS outbox_event (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id     VARCHAR(64)   NOT NULL UNIQUE COMMENT '全局事件ID/消费幂等键',
    event_type   VARCHAR(64)   NOT NULL COMMENT 'ORDER_CREATED/ORDER_PAID/ORDER_CANCELLED',
    payload      TEXT          NOT NULL COMMENT 'JSON 载荷',
    status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SENT/DEAD',
    create_time  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    sent_time    TIMESTAMP     NULL,
    next_retry_time TIMESTAMP  NULL,
    claim_token  VARCHAR(64)   NULL,
    claimed_until TIMESTAMP    NULL,
    last_error   VARCHAR(1000) NULL,
    retries      INT           DEFAULT 0,
    INDEX idx_outbox_status_create (status, create_time),
    INDEX idx_outbox_retry (status, next_retry_time),
    INDEX idx_outbox_claim (status, claimed_until)
);

CREATE TABLE IF NOT EXISTS processed_event (
    event_id       VARCHAR(64) PRIMARY KEY,
    event_type     VARCHAR(64)  NOT NULL,
    order_no       VARCHAR(64)  NULL,
    processed_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_processed_time (processed_time)
);
