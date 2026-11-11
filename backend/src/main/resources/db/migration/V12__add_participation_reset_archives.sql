-- Preserve previous rounds while freeing the participant's current one-time slots.
ALTER TABLE admin_audit_logs DROP CHECK ck_audit_action, DROP CHECK ck_audit_target,
    ADD CONSTRAINT ck_audit_action CHECK(action IN ('MODERATION_DECISION','MODERATION_RETRY','CONTENT_VISIBILITY','GAME_PARTICIPATION_RESET')),
    ADD CONSTRAINT ck_audit_target CHECK(target_type IN ('CONTENT','MODERATION_JOB','USER'));

ALTER TABLE content_items ADD COLUMN archived_at DATETIME(6) NULL,
    DROP INDEX uk_content_slot,
    DROP COLUMN slot_kind,
    ADD COLUMN slot_kind VARCHAR(10) GENERATED ALWAYS AS
      (CASE WHEN archived_at IS NULL AND kind IN ('WISH','CHEER') THEN kind ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_content_slot(author_id,slot_kind);

ALTER TABLE tap_attempts ADD COLUMN archived_at DATETIME(6) NULL,
    DROP CHECK ck_tap_status,
    ADD CONSTRAINT ck_tap_status CHECK (status IN ('COUNTDOWN','CANCELLED','INTERRUPTED','VALID','INVALID','REVIEW_REQUIRED','EXPIRED'));

CREATE TABLE game_participation_resets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(300) NOT NULL,
    snapshot_json JSON NOT NULL,
    summary_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_reset_generation UNIQUE (user_id,generation),
    CONSTRAINT uk_reset_request UNIQUE (actor_id,request_key),
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reset_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT ck_reset_generation CHECK (generation > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
