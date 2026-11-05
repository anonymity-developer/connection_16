-- Operator actions are separate from immutable content and moderation evidence.
CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    before_json JSON NOT NULL,
    after_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_audit_action(action,id),
    INDEX ix_audit_target(target_type,target_id,id),
    CONSTRAINT fk_audit_actor FOREIGN KEY(actor_id) REFERENCES users(id),
    CONSTRAINT ck_audit_action CHECK(action IN ('MODERATION_DECISION','MODERATION_RETRY','CONTENT_VISIBILITY')),
    CONSTRAINT ck_audit_target CHECK(target_type IN ('CONTENT','MODERATION_JOB')),
    CONSTRAINT ck_audit_target_id CHECK(target_id > 0),
    CONSTRAINT ck_audit_reason CHECK(CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 300)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE moderation_jobs
    ADD COLUMN manual_retry_count INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_manual_retry_count CHECK(manual_retry_count BETWEEN 0 AND 3),
    ADD INDEX ix_moderation_queue(status,id);
ALTER TABLE content_items ADD INDEX ix_admin_content(kind,visibility_status,id);
