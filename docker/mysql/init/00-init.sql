-- =========================================
-- Docker MySQL 初始化脚本（完整版）
-- 包含：建表 + 索引 + 初始数据
-- 仅在 Docker MySQL 首次启动时执行
-- =========================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER DATABASE guangying CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON guangying.* TO 'guangying'@'%';
FLUSH PRIVILEGES;

USE guangying;

-- =====================================================
-- 表结构
-- =====================================================

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
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT           DEFAULT 0,
    INDEX idx_movie_status (movie_status, deleted, sort_order),
    INDEX idx_movie_wish (wish),
    INDEX idx_movie_year (release_year, movie_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS city (
    id  BIGINT PRIMARY KEY               COMMENT '城市ID',
    nm  VARCHAR(50)  NOT NULL             COMMENT '城市名',
    py  VARCHAR(100)                      COMMENT '拼音'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INT DEFAULT 0,
    INDEX idx_cinema_city (city_id, deleted, sort_order),
    INDEX idx_cinema_brand (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cinema_brand (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    count INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS district (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    city_id   BIGINT  NOT NULL,
    parent_id BIGINT  DEFAULT 0   COMMENT '0=顶级行政区',
    count     INT     DEFAULT 0,
    INDEX idx_district_city (city_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS subway (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    city_id   BIGINT  NOT NULL,
    parent_id BIGINT  DEFAULT 0   COMMENT '0=地铁线,>0=站点',
    count     INT     DEFAULT 0,
    INDEX idx_subway_city (city_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_type (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    count INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hall_type (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    count INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cinema_service_rel (
    cinema_id  BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    PRIMARY KEY (cinema_id, service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cinema_hall_type_rel (
    cinema_id    BIGINT NOT NULL,
    hall_type_id BIGINT NOT NULL,
    PRIMARY KEY (cinema_id, hall_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account        VARCHAR(100) NOT NULL UNIQUE,
    password       VARCHAR(200) NOT NULL,
    user_nick      VARCHAR(100),
    user_head_img  VARCHAR(500),
    points         INT DEFAULT 0        COMMENT '用户积分(1积分=1元)',
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        INT DEFAULT 0,
    INDEX idx_user_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT           DEFAULT 0,
    INDEX idx_schedule_movie (movie_id, show_date, deleted),
    INDEX idx_schedule_cinema (cinema_id, show_date, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(64)   NOT NULL UNIQUE COMMENT '订单编号',
    user_id         BIGINT        NOT NULL      COMMENT '用户ID',
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
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT           DEFAULT 0,
    INDEX idx_order_user (user_id, status, deleted),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_wish (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL      COMMENT '用户ID',
    movie_id        BIGINT        NOT NULL      COMMENT '电影ID',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_user_wish_unique (user_id, movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT           DEFAULT 0,
    INDEX idx_hall_cinema (cinema_id, deleted),
    UNIQUE INDEX idx_hall_unique (cinema_id, hall_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_seat_lock_schedule (schedule_id, status),
    UNIQUE INDEX idx_seat_lock_unique (schedule_id, row_num, col_num),
    INDEX idx_seat_lock_user (user_id, status),
    INDEX idx_seat_lock_token (lock_token),
    INDEX idx_seat_lock_expire (lock_until, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_seat (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT        NOT NULL      COMMENT '订单ID',
    order_no        VARCHAR(64)   NOT NULL      COMMENT '订单编号',
    schedule_id     BIGINT        NOT NULL      COMMENT '场次ID',
    row_num         INT           NOT NULL      COMMENT '行号',
    col_num         INT           NOT NULL      COMMENT '列号',
    seat_label      VARCHAR(20)   NOT NULL      COMMENT '座位标签(如5排3座)',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_seat_order (order_id),
    INDEX idx_order_seat_schedule (schedule_id),
    UNIQUE INDEX idx_order_seat_unique (schedule_id, row_num, col_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Outbox 事件表（业务事务与消息最终一致）
CREATE TABLE IF NOT EXISTS outbox_event (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id     VARCHAR(64) NOT NULL UNIQUE COMMENT '全局事件ID/消费幂等键',
    event_type   VARCHAR(64) NOT NULL COMMENT 'ORDER_CREATED/ORDER_PAID/ORDER_CANCELLED/ORDER_TIMEOUT',
    payload      TEXT        NOT NULL COMMENT 'JSON 载荷',
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SENT/DEAD',
    create_time  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    sent_time    TIMESTAMP   NULL,
    next_retry_time TIMESTAMP NULL,
    claim_token  VARCHAR(64) NULL,
    claimed_until TIMESTAMP NULL,
    last_error   VARCHAR(1000) NULL,
    retries      INT         DEFAULT 0,
    INDEX idx_outbox_status_create (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS processed_event (
    event_id       VARCHAR(64) PRIMARY KEY,
    event_type     VARCHAR(64) NOT NULL,
    order_no       VARCHAR(64) NULL,
    processed_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_processed_time (processed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================
-- 初始数据
-- =====================================================

-- ==================== 正在热映电影 ====================
INSERT INTO movie (id, nm, enm, img, sc, star, cat, src, dur, pub_desc, dra, wish, vd, photos, pn, show_info, coming_title, movie_status, global_released, sort_order) VALUES
(1,  '逐光者',       'The Light Chaser',      'https://picsum.photos/seed/movie1/180/250',  9.2, '张译,吴京,黄渤',     '剧情,科幻',   '中国大陆', 128, '2026-01-15中国大陆上映', '在不远的未来，一位天才物理学家发现了超越光速的秘密，但这个发现将他推入了一个关于时间与命运的漩涡。', 52890, '', '[]', 0, '今天28家影院放映356场',  NULL, 1, 1, 1),
(2,  '长安幻夜',     'Chang''an Fantasy',     'https://picsum.photos/seed/movie2/180/250',  8.8, '易烊千玺,赵丽颖',    '奇幻,古装',   '中国大陆', 136, '2026-01-20中国大陆上映', '盛唐长安，一场突如其来的妖异事件打破了繁华盛世的宁静，少年剑客与女巫师联手揭开惊天阴谋。', 38920, '', '[]', 0, '今天25家影院放映298场',  NULL, 1, 1, 2),
(3,  '无声的证人',   'Silent Witness',        'https://picsum.photos/seed/movie3/180/250',  8.5, '朱一龙,倪妮',        '悬疑,犯罪',   '中国大陆', 118, '2026-01-22中国大陆上映', '法医林默在一具尸体上发现了不可思议的线索，随着调查深入，一个横跨十年的连环案浮出水面。', 27650, '', '[]', 0, '今天22家影院放映276场',  NULL, 1, 1, 3),
(4,  '深海迷航',     'Deep Sea Odyssey',      'https://picsum.photos/seed/movie4/180/250',  8.3, '彭于晏,刘亦菲',      '冒险,科幻',   '中国大陆', 142, '2026-01-25中国大陆上映', '深海探险队在马里亚纳海沟发现了一座远古文明遗迹，但守护遗迹的神秘力量正在苏醒。', 34500, '', '[]', 0, '今天20家影院放映245场',  NULL, 1, 1, 4),
(5,  '平凡英雄',     'Ordinary Hero',         'https://picsum.photos/seed/movie5/180/250',  9.0, '黄渤,张译',          '剧情',        '中国大陆', 115, '2026-01-28中国大陆上映', '一个普通的外卖小哥，在一次偶然的事件中挺身而出，用平凡的力量书写了不平凡的故事。', 45600, '', '[]', 0, '今天30家影院放映380场',  NULL, 1, 1, 5),
(6,  '星际信使',     'Interstellar Messenger', 'https://picsum.photos/seed/movie6/180/250', 8.1, '王一博,杨紫',        '科幻,动作',   '中国大陆', 130, '2026-02-01中国大陆上映', '来自遥远星系的信号被地球截获，一支精英小队踏上了跨越星际的冒险旅程。', 29800, '', '[]', 0, '今天18家影院放映220场',  NULL, 1, 1, 6),
(7,  '夏日重现',     'Summer Rewind',         'https://picsum.photos/seed/movie7/180/250',  8.6, '刘昊然,周冬雨',      '爱情,奇幻',   '中国大陆', 108, '2026-02-03中国大陆上映', '一个能够回到过去夏天的神奇能力，让他有机会重新面对那段未完成的初恋。', 41200, '', '[]', 0, '今天26家影院放映320场',  NULL, 1, 1, 7),
(8,  '风暴前线',     'Storm Front',           'https://picsum.photos/seed/movie8/180/250',  8.0, '吴京,段奕宏',        '动作,战争',   '中国大陆', 145, '2026-02-05中国大陆上映', '国际维和部队在非洲遭遇恐怖组织袭击，一场惊心动魄的营救行动就此展开。', 35100, '', '[]', 0, '今天24家影院放映290场',  NULL, 1, 1, 8),
(9,  '最后的守护',   'The Last Guardian',     'https://picsum.photos/seed/movie9/180/250',  8.7, '成龙,甄子丹',        '奇幻,动作',   '中国大陆', 125, '2026-02-08中国大陆上映', '传说中守护东方的四灵即将陨落，一位年迈的武术家踏上了寻找新守护者的旅途。', 38700, '', '[]', 0, '今天27家影院放映340场',  NULL, 1, 1, 9),
(10, '追风少年',     'Wind Runner',           'https://picsum.photos/seed/movie10/180/250', 8.4, '吴磊,张子枫',        '运动,青春',   '中国大陆', 112, '2026-02-10中国大陆上映', '一个来自山村的少年，凭借过人的天赋和不屈的意志，在短跑赛场上追逐着奥运梦。', 25800, '', '[]', 0, '今天19家影院放映230场',  NULL, 1, 1, 10),
(11, '迷雾森林',     'Misty Woods',           'https://picsum.photos/seed/movie11/180/250', 7.8, '李现,春夏',          '恐怖,悬疑',   '中国大陆', 105, '2026-02-11中国大陆上映', '六名大学生进入一片被浓雾笼罩的原始森林，等待他们的是远超想象的恐怖真相。', 18900, '', '[]', 0, '今天15家影院放映186场',  NULL, 1, 1, 11),
(12, '时光邮递员',   'Time Postman',          'https://picsum.photos/seed/movie12/180/250', 8.9, '邓超,孙俪',          '爱情,奇幻',   '中国大陆', 118, '2026-02-12中国大陆上映', '一个能递送跨越时空信件的邮递员，在一封来自未来的信中发现了自己命运的秘密。', 42100, '', '[]', 0, '今天23家影院放映285场',  NULL, 1, 1, 12),
(13, '破晓行动',     'Dawn Operation',        'https://picsum.photos/seed/movie13/180/250', 7.9, '张涵予,黄轩',        '动作,谍战',   '中国大陆', 132, '2026-02-13中国大陆上映', '1949年上海解放前夕，我方地下工作者展开一场惊心动魄的情报战。', 21500, '', '[]', 0, '今天17家影院放映210场',  NULL, 1, 1, 13),
(14, '月球基地',     'Moon Base',             'https://picsum.photos/seed/movie14/180/250', 8.2, '刘德华,古天乐',      '科幻',        '中国香港', 127, '2026-02-14中国大陆上映', '人类在月球建立的第一个永久基地遭遇了来自月球深处的未知威胁。', 31200, '', '[]', 0, '今天21家影院放映260场',  NULL, 1, 1, 14),
(15, '烟火人间',     'Fireworks of Life',     'https://picsum.photos/seed/movie15/180/250', 9.1, '葛优,巩俐',          '剧情,喜剧',   '中国大陆', 110, '2026-02-15中国大陆上映', '三个普通家庭在春节前后发生的温暖故事，笑中带泪，展现真实的中国人情味。', 48500, '', '[]', 0, '今天32家影院放映400场',  NULL, 1, 1, 15),
(16, '暗夜猎手',     'Night Hunter',          'https://picsum.photos/seed/movie16/180/250', 7.6, '谢霆锋,甄子丹',      '动作,犯罪',   '中国香港', 120, '2026-02-10中国大陆上映', '一名退役特工被迫重出江湖，追踪一个在暗夜中活动的神秘犯罪集团。', 22300, '', '[]', 0, '今天16家影院放映198场',  NULL, 1, 1, 16),
(17, '流浪地球3',    'The Wandering Earth 3', 'https://picsum.photos/seed/movie17/180/250', 9.3, '吴京,刘德华,沈腾',   '科幻,灾难',   '中国大陆', 168, '2026-02-01中国大陆上映', '地球流浪计划进入关键阶段，人类面临有史以来最严峻的生存考验。', 89200, '', '[]', 0, '今天35家影院放映420场',  NULL, 1, 1, 17),
(18, '漫长的季节',   'A Long Season',         'https://picsum.photos/seed/movie18/180/250', 8.8, '范伟,秦昊',          '剧情,悬疑',   '中国大陆', 135, '2026-02-05中国大陆上映', '东北小城，三个时空交织的故事，揭开一桩尘封二十年的命案真相。', 36800, '', '[]', 0, '今天20家影院放映250场',  NULL, 1, 1, 18),
(19, '冰封侠',       'Ice Knight',            'https://picsum.photos/seed/movie19/180/250', 7.5, '甄子丹,王宝强',      '动作,奇幻',   '中国大陆', 115, '2026-02-08中国大陆上映', '一位被冰封千年的古代武将在现代苏醒，面对全新的世界和旧日的宿敌。', 19500, '', '[]', 0, '今天14家影院放映175场',  NULL, 1, 1, 19),
(20, '心灵奇旅2',    'Soul Journey 2',        'https://picsum.photos/seed/movie20/180/250', 8.5, '配音:何炅,黄磊',     '动画,奇幻',   '美国',     98,  '2026-02-12中国大陆上映', '小灵魂22号在地球上找到了自己的spark，但一次意外让她回到了灵魂世界。', 33400, '', '[]', 0, '今天25家影院放映310场',  NULL, 1, 1, 20),
(21, '绝境逢生',     'Against All Odds',      'https://picsum.photos/seed/movie21/180/250', 7.7, '王千源,刘昊然',      '动作,冒险',   '中国大陆', 122, '2026-02-06中国大陆上映', '一支登山队在暴风雪中被困喜马拉雅，生死之间展开艰难自救。', 20100, '', '[]', 0, '今天13家影院放映165场',  NULL, 1, 1, 21),
(22, '人间清醒',     'Clear Mind',            'https://picsum.photos/seed/movie22/180/250', 8.3, '雷佳音,马丽',        '喜剧,剧情',   '中国大陆', 105, '2026-02-09中国大陆上映', '一个中年男人在人生低谷时获得了"看透一切"的能力，由此引发一系列啼笑皆非的故事。', 28600, '', '[]', 0, '今天22家影院放映270场',  NULL, 1, 1, 22),
(23, '海上钢琴师',   'The Pianist at Sea',    'https://picsum.photos/seed/movie23/180/250', 9.0, '王凯,谭松韵',        '音乐,剧情',   '中国大陆', 130, '2026-02-14中国大陆上映', '一位在邮轮上弹奏钢琴的盲人乐师，与一位年轻画家之间跨越障碍的动人故事。', 37800, '', '[]', 0, '今天19家影院放映240场',  NULL, 1, 1, 23),
(24, '无名之城',     'The Nameless City',     'https://picsum.photos/seed/movie24/180/250', 8.1, '朱一龙,王俊凯',      '悬疑,科幻',   '中国大陆', 128, '2026-02-11中国大陆上映', '一座没有名字的地下城市被意外发现，进入其中的考古队发现这里隐藏着改变世界的秘密。', 25400, '', '[]', 0, '今天16家影院放映200场',  NULL, 1, 1, 24),
(25, '大话西游之后传', 'Journey West Sequel',  'https://picsum.photos/seed/movie25/180/250', 7.8, '沈腾,马丽',          '喜剧,奇幻',   '中国大陆', 110, '2026-02-03中国大陆上映', '至尊宝在500年后再次醒来，紫霞仙子却已经忘记了一切，一段新的寻爱之旅就此开始。', 43200, '', '[]', 0, '今天28家影院放映350场',  NULL, 1, 1, 25);

-- ==================== 即将上映电影 ====================
INSERT INTO movie (id, nm, enm, img, sc, star, cat, src, dur, pub_desc, dra, wish, vd, photos, pn, show_info, coming_title, movie_status, global_released, sort_order) VALUES
(26, '明日边缘',     'Edge of Tomorrow',      'https://picsum.photos/seed/movie26/180/250', 0, '李现,杨幂',          '科幻,动作',   '中国大陆', 135, '2026年3月15日上映', '一名普通士兵在未来战场上获得了时间循环能力，在无数次"死亡"中成长为拯救世界的英雄。', 67800, '', '[]', 0, NULL, '3月15日 周日', 0, 0, 1),
(27, '春风化雨',     'Spring Rain',           'https://picsum.photos/seed/movie27/180/250', 0, '黄渤,海清',          '剧情',        '中国大陆', 118, '2026年3月18日上映', '乡村教师用二十年的坚守，改变了一代又一代山村孩子的命运。', 42300, '', '[]', 0, NULL, '3月18日 周三', 0, 0, 2),
(28, '龙族崛起',     'Rise of Dragons',       'https://picsum.photos/seed/movie28/180/250', 0, '吴京,赵丽颖',        '奇幻,动作',   '中国大陆', 150, '2026年3月20日上映', '远古龙族的最后传人觉醒了沉睡千年的龙血力量，一场人与龙的史诗战争即将爆发。', 78500, '', '[]', 0, NULL, '3月20日 周五', 0, 0, 3),
(29, '末日旅人',     'Doomsday Traveler',     'https://picsum.photos/seed/movie29/180/250', 0, '张译,于和伟',        '科幻,灾难',   '中国大陆', 140, '2026年3月22日上映', '小行星撞击倒计时72小时，一位父亲穿越废墟寻找失散的女儿。', 56200, '', '[]', 0, NULL, '3月22日 周日', 0, 0, 4),
(30, '少年的你2',    'Better Days 2',         'https://picsum.photos/seed/movie30/180/250', 0, '易烊千玺,周冬雨',    '剧情,犯罪',   '中国大陆', 125, '2026年3月28日上映', '成年后的陈念成为一名心理咨询师，在帮助一个少年的过程中再次面对自己的过去。', 85600, '', '[]', 0, NULL, '3月28日 周六', 0, 0, 5),
(31, '天空之门',     'Gate of the Sky',       'https://picsum.photos/seed/movie31/180/250', 0, '彭于晏,刘诗诗',      '奇幻,冒险',   '中国大陆', 132, '2026年4月1日上映',  '传说天空中有一扇通往另一个世界的门，一对探险家夫妻决定找到它。', 34500, '', '[]', 0, NULL, '4月1日 周三', 0, 0, 6),
(32, '非常嫌疑犯',   'Unusual Suspects',      'https://picsum.photos/seed/movie32/180/250', 0, '段奕宏,廖凡',        '悬疑,犯罪',   '中国大陆', 120, '2026年4月5日上映',  '五名互不相识的嫌疑人在审讯室中各执一词，真相隐藏在每个人的谎言之下。', 41200, '', '[]', 0, NULL, '4月5日 周日', 0, 0, 7),
(33, '银河补习班2',  'Galaxy Academy 2',      'https://picsum.photos/seed/movie33/180/250', 0, '邓超,白宇',          '剧情,喜剧',   '中国大陆', 115, '2026年4月10日上映', '马飞成为航天员后回到家乡，发现自己的儿子正面临和他当年一样的困境。', 38900, '', '[]', 0, NULL, '4月10日 周五', 0, 0, 8),
(34, '功夫熊猫5',    'Kung Fu Panda 5',       'https://picsum.photos/seed/movie34/180/250', 0, '配音:杰克·布莱克',   '动画,喜剧',   '美国',     95,  '2026年4月15日上映', '阿宝面对来自灵界的终极威胁，必须学习最神秘的功夫技艺来保护和平谷。', 72100, '', '[]', 0, NULL, '4月15日 周三', 0, 0, 9),
(35, '极速营救',     'Speed Rescue',          'https://picsum.photos/seed/movie35/180/250', 0, '谢霆锋,张家辉',      '动作,犯罪',   '中国香港', 118, '2026年4月18日上映', '劫匪在高速行驶的列车上绑架了数百名乘客，一名卧底警察必须在天亮前解救所有人。', 29800, '', '[]', 0, NULL, '4月18日 周六', 0, 0, 10),
(36, '梦游仙境',     'Wonderland Dreams',     'https://picsum.photos/seed/movie36/180/250', 0, '关晓彤,张新成',      '奇幻,冒险',   '中国大陆', 105, '2026年4月22日上映', '一位失眠症少女在梦中进入了一个奇幻世界，在那里她必须找到回家的路。', 25600, '', '[]', 0, NULL, '4月22日 周三', 0, 0, 11),
(37, '致命密码',     'Fatal Code',            'https://picsum.photos/seed/movie37/180/250', 0, '李现,杨紫',          '悬疑,科技',   '中国大陆', 122, '2026年4月28日上映', '一段被加密的AI代码引发连环命案，程序员和女警联手追查数字世界背后的杀机。', 31400, '', '[]', 0, NULL, '4月28日 周二', 0, 0, 12),
(38, '天涯客',       'Traveler''s Tale',      'https://picsum.photos/seed/movie38/180/250', 0, '肖战,王一博',        '武侠,冒险',   '中国大陆', 128, '2026年5月1日上映',  '两个性格迥异的江湖侠客因一把神秘宝刀相遇，在险恶的武林中结下了深厚情谊。', 95800, '', '[]', 0, NULL, '5月1日 周五', 0, 0, 13),
(39, '超能家庭',     'Super Family',          'https://picsum.photos/seed/movie39/180/250', 0, '沈腾,贾玲',          '喜剧,科幻',   '中国大陆', 108, '2026年5月5日上映',  '一家四口在一次实验事故后都获得了超能力，但他们发现拯救世界远没有搞定家务事难。', 51200, '', '[]', 0, NULL, '5月5日 周二', 0, 0, 14),
(40, '迷失东京',     'Lost in Tokyo',         'https://picsum.photos/seed/movie40/180/250', 0, '刘昊然,桥本环奈',    '爱情,剧情',   '中国大陆/日本', 115, '2026年5月10日上映', '两个来自不同国度的年轻人在东京相遇，语言不通却在这座城市中找到了彼此。', 44300, '', '[]', 0, NULL, '5月10日 周日', 0, 0, 15),
(41, '幻影追踪',     'Phantom Tracker',       'https://picsum.photos/seed/movie41/180/250', 0, '张涵予,彭于晏',      '动作,悬疑',   '中国大陆', 130, '2026年5月15日上映', '一名退休侦探被一桩离奇失踪案吸引重出江湖，追踪过程中发现了一个庞大的地下网络。', 22100, '', '[]', 0, NULL, '5月15日 周五', 0, 0, 16),
(42, '我的阿勒泰',   'My Altay',              'https://picsum.photos/seed/movie42/180/250', 0, '马伊琍,于适',        '剧情,文艺',   '中国大陆', 110, '2026年5月20日上映', '一个城市女孩来到阿勒泰草原，在广袤的天地间找到了生命的意义。', 36700, '', '[]', 0, NULL, '5月20日 周三', 0, 0, 17),
(43, '再见哥斯拉',   'Goodbye Godzilla',      'https://picsum.photos/seed/movie43/180/250', 0, '渡边谦',             '科幻,灾难',   '日本',     138, '2026年5月28日上映', '哥斯拉消失三十年后再次出现，这一次它要面对的不是人类的武器，而是另一个远古巨兽。', 58900, '', '[]', 0, NULL, '5月28日 周四', 0, 0, 18),
(44, '情书2026',     'Love Letter 2026',      'https://picsum.photos/seed/movie44/180/250', 0, '王俊凯,苏晓彤',      '爱情',        '中国大陆', 100, '2026年6月1日上映',  '一封穿越二十年的情书，连接了两代人的青春与爱情，温暖而治愈。', 33100, '', '[]', 0, NULL, '6月1日 周一', 0, 0, 19),
(45, '第二十条2',    'Article 20 Part 2',     'https://picsum.photos/seed/movie45/180/250', 0, '雷佳音,马丽',        '剧情,法律',   '中国大陆', 125, '2026年6月10日上映', '一位基层检察官在处理一起复杂的公诉案件时，发现法律与人情之间的艰难抉择。', 47200, '', '[]', 0, NULL, '6月10日 周三', 0, 0, 20);


-- ==================== 城市数据 ====================
INSERT INTO city (id, nm, py) VALUES
(1, '北京', 'beijing'), (2, '上海', 'shanghai'), (3, '广州', 'guangzhou'), (4, '深圳', 'shenzhen'),
(5, '成都', 'chengdu'), (6, '杭州', 'hangzhou'), (7, '武汉', 'wuhan'), (8, '南京', 'nanjing'),
(9, '西安', 'xian'), (10, '重庆', 'chongqing'), (11, '长沙', 'changsha'), (12, '天津', 'tianjin'),
(13, '苏州', 'suzhou'), (14, '郑州', 'zhengzhou'), (15, '青岛', 'qingdao'), (16, '大连', 'dalian'),
(17, '东莞', 'dongguan'), (18, '厦门', 'xiamen'), (19, '沈阳', 'shenyang'), (20, '合肥', 'hefei'),
(21, '佛山', 'foshan'), (22, '昆明', 'kunming'), (23, '济南', 'jinan'), (24, '福州', 'fuzhou'),
(25, '无锡', 'wuxi'), (26, '哈尔滨', 'haerbin'), (27, '石家庄', 'shijiazhuang'), (28, '温州', 'wenzhou'),
(29, '南宁', 'nanning'), (30, '南昌', 'nanchang'), (31, '宁波', 'ningbo'), (32, '常州', 'changzhou'),
(33, '贵阳', 'guiyang'), (34, '太原', 'taiyuan'), (35, '烟台', 'yantai'), (36, '嘉兴', 'jiaxing'),
(37, '南通', 'nantong'), (38, '金华', 'jinhua'), (39, '珠海', 'zhuhai'), (40, '惠州', 'huizhou'),
(41, '徐州', 'xuzhou'), (42, '海口', 'haikou'), (43, '台州', 'taizhou'), (44, '中山', 'zhongshan'),
(45, '洛阳', 'luoyang'), (46, '潍坊', 'weifang'), (47, '保定', 'baoding'), (48, '兰州', 'lanzhou'),
(49, '绍兴', 'shaoxing'), (50, '泉州', 'quanzhou'), (51, '桂林', 'guilin'), (52, '呼和浩特', 'huhehaote'),
(53, '长春', 'changchun'), (54, '三亚', 'sanya'), (55, '银川', 'yinchuan'), (56, '扬州', 'yangzhou'),
(57, '芜湖', 'wuhu'), (58, '漳州', 'zhangzhou'), (59, '湖州', 'huzhou'), (60, '赣州', 'ganzhou');


-- ==================== 影院品牌 ====================
INSERT INTO cinema_brand (id, name, count) VALUES
(1, '万达影城', 8), (2, '星美国际', 5), (3, '金逸影城', 4),
(4, '大地影院', 6), (5, 'CGV影城', 3), (6, '百老汇影城', 2),
(7, '博纳国际', 4), (8, '中影国际', 3), (9, '横店影城', 5),
(10, '耀莱成龙', 2);


-- ==================== 行政区（北京） ====================
INSERT INTO district (id, name, city_id, parent_id, count) VALUES
(1, '朝阳区', 1, 0, 10), (2, '海淀区', 1, 0, 7), (3, '东城区', 1, 0, 4),
(4, '西城区', 1, 0, 3), (5, '丰台区', 1, 0, 5), (6, '通州区', 1, 0, 3),
(101, '大望路', 1, 1, 3), (102, '三里屯', 1, 1, 2), (103, '望京', 1, 1, 3), (104, '国贸', 1, 1, 2),
(201, '五道口', 1, 2, 2), (202, '中关村', 1, 2, 3), (203, '西直门', 1, 2, 2),
(301, '王府井', 1, 3, 2), (302, '东直门', 1, 3, 2),
(401, '西单', 1, 4, 2), (402, '金融街', 1, 4, 1),
(501, '方庄', 1, 5, 2), (502, '大红门', 1, 5, 2), (503, '丽泽', 1, 5, 1);


-- ==================== 地铁（北京） ====================
INSERT INTO subway (id, name, city_id, parent_id, count) VALUES
(1, '1号线', 1, 0, 6), (2, '2号线', 1, 0, 4), (3, '10号线', 1, 0, 8),
(4, '6号线', 1, 0, 3), (5, '14号线', 1, 0, 4),
(101, '国贸站', 1, 1, 3), (102, '大望路站', 1, 1, 2), (103, '王府井站', 1, 1, 2),
(201, '西直门站', 1, 2, 2), (202, '东直门站', 1, 2, 2),
(301, '三里屯站', 1, 3, 2), (302, '望京站', 1, 3, 3), (303, '国贸站', 1, 3, 3),
(401, '金台路站', 1, 4, 1), (402, '青年路站', 1, 4, 2),
(501, '大望路站', 1, 5, 2), (502, '方庄站', 1, 5, 2);


-- ==================== 服务类型 ====================
INSERT INTO service_type (id, name, count) VALUES
(1, '退票', 18), (2, '改签', 15), (3, '小吃', 20), (4, '折扣卡', 10);


-- ==================== 厅型 ====================
INSERT INTO hall_type (id, name, count) VALUES
(1, 'IMAX', 8), (2, '杜比全景声', 6), (3, '4DX', 3),
(4, '中国巨幕', 5), (5, '激光厅', 10), (6, '杜比影院', 4);


-- ==================== 影院数据（北京） ====================
INSERT INTO cinema (id, nm, addr, city_id, brand_id, district_id, area_id, distance, allow_refund, endorse, snack, vip_tag, hall_types_json, card_promotion_tag, sort_order) VALUES
(1,  '万达影城(朝阳大悦城店)',   '北京市朝阳区朝阳北路101号大悦城9层',           1, 1, 1, 101, '1.2km', 1, 1, 1, 'VIP厅',  '["IMAX","杜比全景声"]',  '新客立减10元', 1),
(2,  '星美国际影城(三里屯店)',   '北京市朝阳区三里屯路19号三里屯太古里南区B1', 1, 2, 1, 102, '2.3km', 1, 0, 1, '',       '["杜比全景声"]',         '会员9折',      2),
(3,  '金逸影城(望京店)',         '北京市朝阳区望京街9号望京国际商业中心B1层',   1, 3, 1, 103, '3.5km', 1, 1, 1, 'VIP厅',  '["IMAX","4DX"]',         '',             3),
(4,  'CGV影城(国贸店)',          '北京市朝阳区建国门外大街1号国贸商城B2层',     1, 5, 1, 104, '1.8km', 1, 1, 1, 'GOLD CLASS', '["IMAX","杜比影院","4DX"]', '特惠购票', 4),
(5,  '万达影城(五棵松店)',       '北京市海淀区复兴路69号五棵松华熙LIVE 4层',    1, 1, 2, 202, '4.2km', 1, 1, 1, 'VIP厅',  '["IMAX","中国巨幕"]',    '万达卡9.9折',  5),
(6,  '博纳国际影城(五道口店)',   '北京市海淀区成府路28号华联购物中心5层',        1, 7, 2, 201, '5.1km', 1, 0, 1, '',       '["激光厅"]',             '',             6),
(7,  '横店影城(中关村店)',       '北京市海淀区中关村大街19号新中关大厦B1层',     1, 9, 2, 202, '4.8km', 1, 1, 0, '',       '["杜比全景声","激光厅"]', '双人套票特惠', 7),
(8,  '大地影院(西直门店)',       '北京市海淀区西直门外大街1号嘉茂购物中心4层',   1, 4, 2, 203, '3.9km', 0, 0, 1, '',       '["激光厅"]',             '',             8),
(9,  '万达影城(王府井店)',       '北京市东城区王府井大街138号新东安广场7层',      1, 1, 3, 301, '2.0km', 1, 1, 1, 'VIP厅',  '["IMAX","杜比全景声","中国巨幕"]', '限时立减', 9),
(10, '中影国际影城(东直门店)',   '北京市东城区东直门外大街48号东方银座B1层',      1, 8, 3, 302, '3.2km', 1, 1, 0, '',       '["激光厅","杜比全景声"]', '',            10),
(11, '星美国际影城(西单店)',     '北京市西城区西单北大街131号大悦城10层',          1, 2, 4, 401, '2.5km', 1, 0, 1, '',       '["杜比全景声"]',         '会员日半价',  11),
(12, '耀莱成龙影城(西单店)',     '北京市西城区西单北大街180号3层',                1, 10, 4, 401, '2.6km', 1, 1, 1, 'VIP厅', '["IMAX"]',               '',            12),
(13, '万达影城(方庄店)',         '北京市丰台区方庄路18号方庄时代广场4层',          1, 1, 5, 501, '5.5km', 1, 1, 1, '',       '["IMAX","激光厅"]',      '工作日特惠',  13),
(14, '金逸影城(大红门店)',       '北京市丰台区大红门西路9号鑫福海购物广场4层',     1, 3, 5, 502, '6.8km', 0, 0, 1, '',       '["激光厅"]',             '',            14),
(15, '大地影院(丽泽店)',         '北京市丰台区丽泽路16号丽泽SOHO B1层',           1, 4, 5, 503, '5.0km', 1, 0, 0, '',       '["中国巨幕"]',           '',            15),
(16, '万达影城(通州万达店)',     '北京市通州区新华西街58号万达广场4层',             1, 1, 6, 0,   '12.5km', 1, 1, 1, 'VIP厅', '["IMAX","杜比全景声"]',  '开卡送爆米花', 16),
(17, '星美国际影城(通州北苑店)', '北京市通州区北苑南路42号新光大中心3层',           1, 2, 6, 0,   '13.2km', 1, 0, 1, '',      '["激光厅"]',             '',            17),
(18, '大地影院(通州店)',         '北京市通州区杨庄路30号贵友大厦5层',               1, 4, 6, 0,   '14.0km', 0, 0, 0, '',      '[]',                     '',            18),
(19, '横店影城(望京新荟城店)',   '北京市朝阳区广顺南大街16号新荟城3层',             1, 9, 1, 103, '3.2km', 1, 1, 1, '',      '["杜比全景声","激光厅"]', '横店会员日',  19),
(20, '博纳国际影城(朝阳门店)',   '北京市朝阳区朝阳门外大街18号丰联广场B1层',       1, 7, 1, 104, '1.5km', 1, 0, 1, '',      '["IMAX"]',               '特价场',      20),
(21, '中影国际影城(安贞店)',     '北京市朝阳区安贞路1号安贞华联B1层',               1, 8, 1, 0,   '4.0km', 1, 1, 0, '',      '["中国巨幕","激光厅"]',  '',            21),
(22, '万达影城(石景山万达店)',   '北京市石景山区石景山路乙32号万达广场4层',          1, 1, 0, 0,   '8.5km', 1, 1, 1, '',      '["IMAX"]',               '万达特惠',    22),
(23, 'CGV影城(颐堤港店)',        '北京市朝阳区酒仙桥路18号颐堤港3层',               1, 5, 1, 103, '3.8km', 1, 1, 1, 'GOLD CLASS', '["IMAX","杜比影院"]', '',        23),
(24, '金逸影城(中关村店)',       '北京市海淀区中关村南大街2号数码大厦B1层',          1, 3, 2, 202, '5.0km', 1, 0, 1, '',      '["杜比全景声","激光厅"]', '金逸卡优惠',  24),
(25, '博纳国际影城(亦庄店)',     '北京市大兴区经海三路11号亦庄经开万达广场3层',      1, 7, 0, 0,   '18.0km', 1, 1, 1, '',     '["IMAX","中国巨幕"]',    '周末特惠',    25);


-- ==================== 影院-服务关联 ====================
INSERT INTO cinema_service_rel (cinema_id, service_id) VALUES
(1,1),(1,2),(1,3),(1,4), (2,1),(2,3), (3,1),(3,2),(3,3), (4,1),(4,2),(4,3),(4,4),
(5,1),(5,2),(5,3),(5,4), (6,1),(6,3), (7,1),(7,2), (8,3),
(9,1),(9,2),(9,3),(9,4), (10,1),(10,2), (11,1),(11,3), (12,1),(12,2),(12,3),
(13,1),(13,2),(13,3), (14,3), (15,1),
(16,1),(16,2),(16,3),(16,4), (17,1),(17,3), (18,3),
(19,1),(19,2),(19,3), (20,1),(20,3), (21,1),(21,2),
(22,1),(22,2),(22,3), (23,1),(23,2),(23,3),(23,4), (24,1),(24,3), (25,1),(25,2),(25,3);


-- ==================== 影院-厅型关联 ====================
INSERT INTO cinema_hall_type_rel (cinema_id, hall_type_id) VALUES
(1,1),(1,2), (2,2), (3,1),(3,3), (4,1),(4,3),(4,6),
(5,1),(5,4), (6,5), (7,2),(7,5), (8,5),
(9,1),(9,2),(9,4), (10,2),(10,5), (11,2), (12,1),
(13,1),(13,5), (14,5), (15,4),
(16,1),(16,2), (17,5), (19,2),(19,5), (20,1),
(21,4),(21,5), (22,1), (23,1),(23,6), (24,2),(24,5), (25,1),(25,4);


-- ==================== 场次数据 ====================
INSERT INTO movie_schedule (id, movie_id, cinema_id, hall_name, show_date, show_time, end_time, lang, total_seats, available_seats, price, status) VALUES
(1,  1, 1, 'IMAX厅',    CURDATE(), '10:00', '12:08', '国语',  200, 180, 59.90, 1),
(2,  1, 1, '杜比全景声厅', CURDATE(), '13:30', '15:38', '国语',  150, 120, 49.90, 1),
(3,  1, 1, '3号厅',      CURDATE(), '16:00', '18:08', '国语',  100,  85, 39.90, 1),
(4,  1, 2, '1号厅',      CURDATE(), '10:30', '12:38', '国语',  120, 100, 42.00, 1),
(5,  1, 2, '2号厅',      CURDATE(), '14:00', '16:08', '国语',  100,  75, 38.00, 1),
(6,  2, 1, 'IMAX厅',    CURDATE(), '19:00', '21:16', '国语',  200, 150, 69.90, 1),
(7,  2, 3, 'IMAX厅',    CURDATE(), '10:00', '12:16', '国语',  180, 160, 55.00, 1),
(8,  2, 3, '4DX厅',     CURDATE(), '14:30', '16:46', '国语',  80,   60, 79.90, 1),
(9,  3, 4, 'IMAX厅',    CURDATE(), '11:00', '12:58', '国语',  220, 190, 65.00, 1),
(10, 3, 4, '杜比影院',   CURDATE(), '15:00', '16:58', '国语',  100,  80, 89.90, 1),
(11, 4, 5, 'IMAX厅',    CURDATE(), '10:00', '12:22', '国语',  200, 170, 55.00, 1),
(12, 4, 5, '中国巨幕厅',  CURDATE(), '14:00', '16:22', '国语',  160, 130, 49.00, 1),
(13, 5, 1, '3号厅',      CURDATE(), '20:00', '21:55', '国语',  100,  90, 35.90, 1),
(14, 5, 9, 'IMAX厅',    CURDATE(), '10:00', '11:55', '国语',  250, 220, 55.00, 1),
(15, 5, 9, '杜比全景声厅', CURDATE(), '15:00', '16:55', '国语',  150, 120, 45.00, 1),
(16, 1, 1, '5号厅',      CURDATE(), '22:00', '00:08', '国语',  80,   3,  29.90, 1),
(17, 2, 2, '3号厅',      CURDATE(), '22:30', '00:46', '国语',  60,   1,  32.00, 1),
(18, 3, 3, '2号厅',      CURDATE(), '21:00', '22:58', '国语',  90,   5,  35.00, 1),
(19, 1, 3, 'IMAX厅',    CURDATE(), '09:30', '11:38', '国语',  180, 160, 55.00, 1),
(20, 1, 4, 'IMAX厅',    CURDATE(), '11:00', '13:08', '国语',  220, 200, 65.00, 1),
(21, 1, 5, 'IMAX厅',    CURDATE(), '13:00', '15:08', '国语',  200, 175, 55.00, 1),
(22, 2, 2, '1号厅',      CURDATE(), '10:00', '12:16', '国语',  120, 100, 42.00, 1),
(23, 2, 4, 'IMAX厅',    CURDATE(), '14:00', '16:16', '国语',  220, 180, 65.00, 1),
(24, 2, 5, '中国巨幕厅',  CURDATE(), '16:30', '18:46', '国语',  160, 140, 49.00, 1),
(25, 3, 1, '杜比全景声厅', CURDATE(), '10:00', '11:58', '国语',  150, 130, 49.90, 1),
(26, 3, 2, '2号厅',      CURDATE(), '13:00', '14:58', '国语',  100,  85, 38.00, 1),
(27, 3, 5, 'IMAX厅',    CURDATE(), '15:30', '17:28', '国语',  200, 170, 55.00, 1),
(28, 4, 1, 'IMAX厅',    CURDATE(), '09:00', '11:22', '国语',  200, 185, 59.90, 1),
(29, 4, 2, '1号厅',      CURDATE(), '12:00', '14:22', '国语',  120,  95, 42.00, 1),
(30, 4, 3, 'IMAX厅',    CURDATE(), '15:00', '17:22', '国语',  180, 155, 55.00, 1),
(31, 5, 2, '2号厅',      CURDATE(), '11:00', '12:55', '国语',  100,  80, 38.00, 1),
(32, 5, 3, '4DX厅',     CURDATE(), '14:00', '15:55', '国语',   80,  65, 79.90, 1),
(33, 5, 4, 'IMAX厅',    CURDATE(), '17:00', '18:55', '国语',  220, 195, 65.00, 1),
(34, 5, 5, 'IMAX厅',    CURDATE(), '19:30', '21:25', '国语',  200, 170, 55.00, 1),
(35, 1, 1, 'IMAX厅',    DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00', '12:08', '国语', 200, 200, 59.90, 1),
(36, 1, 1, '杜比全景声厅', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00', '16:08', '国语', 150, 150, 49.90, 1),
(37, 1, 2, '1号厅',      DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:30', '12:38', '国语', 120, 120, 42.00, 1),
(38, 2, 1, 'IMAX厅',    DATE_ADD(CURDATE(), INTERVAL 1 DAY), '19:00', '21:16', '国语', 200, 200, 69.90, 1),
(39, 2, 3, 'IMAX厅',    DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00', '12:16', '国语', 180, 180, 55.00, 1),
(40, 3, 4, 'IMAX厅',    DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:00', '12:58', '国语', 220, 220, 65.00, 1);


-- ==================== 影院影厅座位布局 ====================
INSERT INTO cinema_hall (id, cinema_id, hall_name, seat_rows, seat_cols, aisle_after_col, couple_rows, disabled_seats, hall_type) VALUES
(1,  1, 'IMAX厅',       10, 20, '4,16', '10', '[[1,1],[1,20],[10,1],[10,20]]', 'IMAX'),
(2,  1, '杜比全景声厅',   8, 14, '3,11', '',   '[]', '杜比全景声'),
(3,  1, '3号厅',          8, 12, '3,9',  '',   '[]', '普通厅'),
(4,  1, '5号厅',          6, 10, '',     '',   '[]', '普通厅'),
(5,  2, '1号厅',          8, 14, '3,11', '8',  '[]', '杜比全景声'),
(6,  2, '2号厅',          8, 12, '3,9',  '',   '[]', '普通厅'),
(7,  2, '3号厅',          6, 10, '',     '',   '[]', '普通厅'),
(8,  3, 'IMAX厅',        10, 18, '4,14', '10', '[[1,1],[1,18]]', 'IMAX'),
(9,  3, '4DX厅',          6, 10, '',     '',   '[]', '4DX'),
(10, 3, '2号厅',          8, 12, '3,9',  '',   '[]', '普通厅'),
(11, 4, 'IMAX厅',        12, 22, '5,17', '12', '[[1,1],[1,2],[1,21],[1,22]]', 'IMAX'),
(12, 4, '杜比影院',        8, 14, '3,11', '',   '[]', '杜比影院'),
(13, 5, 'IMAX厅',        10, 20, '4,16', '10', '[]', 'IMAX'),
(14, 5, '中国巨幕厅',      8, 16, '4,12', '',   '[]', '中国巨幕'),
(15, 9, 'IMAX厅',        12, 22, '5,17', '12', '[]', 'IMAX'),
(16, 9, '杜比全景声厅',    8, 14, '3,11', '',   '[]', '杜比全景声');
