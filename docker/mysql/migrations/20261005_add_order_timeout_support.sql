-- 为“RocketMQ 定时消息主触发 + MySQL 扫描兜底”补充可审计原因和扫描索引。
-- 仅用于升级已有 MySQL 数据卷；全新部署由 00-init.sql 直接创建。
ALTER TABLE ticket_order
    ADD COLUMN cancel_reason VARCHAR(32) NULL COMMENT '取消原因' AFTER cancel_time;

CREATE INDEX idx_order_expire
    ON ticket_order(status, deleted, expire_time, id);
