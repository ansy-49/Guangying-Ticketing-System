-- 仅用于升级早于“建单幂等键”版本的已有 MySQL 数据卷；全新部署无需执行。
ALTER TABLE ticket_order
    ADD COLUMN idempotency_key VARCHAR(64) NULL COMMENT '客户端建单幂等键' AFTER user_id,
    ADD COLUMN request_fingerprint VARCHAR(64) NULL COMMENT '场次与座位请求摘要' AFTER idempotency_key;

-- 历史订单不会参与新请求的幂等重放，使用订单自身信息生成稳定且唯一的占位值。
UPDATE ticket_order
SET idempotency_key = CONCAT('legacy-', id),
    request_fingerprint = SHA2(order_no, 256)
WHERE idempotency_key IS NULL;

ALTER TABLE ticket_order
    MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL COMMENT '客户端建单幂等键',
    MODIFY COLUMN request_fingerprint VARCHAR(64) NOT NULL COMMENT '场次与座位请求摘要';

CREATE UNIQUE INDEX idx_order_idempotency
    ON ticket_order(user_id, idempotency_key);
