CREATE INDEX idx_outbox_retry
    ON outbox_event(status, next_retry_time);

CREATE INDEX idx_outbox_claim
    ON outbox_event(status, claimed_until);
