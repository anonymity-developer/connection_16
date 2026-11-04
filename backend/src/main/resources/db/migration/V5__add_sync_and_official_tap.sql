-- Questionnaires are immutable operator-reviewed JSON. No live questions are seeded.
CREATE TABLE sync_questionnaires (
    version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    definition_json JSON NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE current_sync_questionnaire (
    id TINYINT NOT NULL PRIMARY KEY,
    version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT ck_sync_singleton CHECK (id=1),
    CONSTRAINT fk_current_sync FOREIGN KEY (version) REFERENCES sync_questionnaires(version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sync_results (
    user_id BIGINT NOT NULL PRIMARY KEY,
    questionnaire_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    answers_json JSON NOT NULL,
    axis_scores_json JSON NOT NULL,
    type_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_sync_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_sync_questionnaire FOREIGN KEY (questionnaire_version) REFERENCES sync_questionnaires(version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE tap_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    generation INT NOT NULL,
    idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    submit_deadline DATETIME(6) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'COUNTDOWN',
    tap_count INT NULL,
    submitted_at DATETIME(6) NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    review_reason VARCHAR(40) NULL,
    CONSTRAINT uk_tap_generation UNIQUE (user_id,generation),
    CONSTRAINT uk_tap_request UNIQUE (user_id,idempotency_key),
    CONSTRAINT uk_tap_owner UNIQUE (id,user_id),
    CONSTRAINT fk_tap_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_tap_generation CHECK (generation > 0),
    CONSTRAINT ck_tap_times CHECK (starts_at < ends_at AND ends_at < submit_deadline),
    CONSTRAINT ck_tap_count CHECK (tap_count IS NULL OR tap_count >= 0),
    CONSTRAINT ck_tap_status CHECK (status IN ('COUNTDOWN','CANCELLED','INTERRUPTED','VALID','REVIEW_REQUIRED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE tap_slots (
    user_id BIGINT NOT NULL PRIMARY KEY,
    current_attempt_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT fk_slot_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_slot_owner FOREIGN KEY (current_attempt_id,user_id) REFERENCES tap_attempts(id,user_id),
    CONSTRAINT ck_slot_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE tap_attempt_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    relative_time_ms INT NOT NULL,
    CONSTRAINT fk_tap_event FOREIGN KEY (attempt_id) REFERENCES tap_attempts(id),
    CONSTRAINT ck_tap_event_type CHECK (event_type IN ('TAP','HIDDEN','PAGE_HIDE')),
    INDEX ix_tap_events (attempt_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
