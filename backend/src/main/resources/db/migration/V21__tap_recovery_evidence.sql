-- Preserve the original attempt and deadline while recording an explicit audited reclassification.
CREATE TABLE tap_recoveries (
    attempt_id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    expected_generation BIGINT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    original_status VARCHAR(24) NOT NULL,
    original_reason VARCHAR(64) NULL,
    original_deadline DATETIME(6) NOT NULL,
    effective_deadline DATETIME(6) NOT NULL,
    recognized_at DATETIME(6) NOT NULL,
    restored_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_tap_recovery_request (actor_id,request_key),
    CONSTRAINT fk_tap_recovery_attempt FOREIGN KEY (attempt_id,user_id) REFERENCES tap_attempts(id,user_id),
    CONSTRAINT fk_tap_recovery_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT ck_tap_recovery_deadline CHECK (recognized_at<=effective_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
