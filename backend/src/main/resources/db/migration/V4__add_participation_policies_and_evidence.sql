CREATE TABLE event_settings (
    id TINYINT NOT NULL PRIMARY KEY,
    opens_at DATETIME(6) NOT NULL,
    submit_closes_at DATETIME(6) NOT NULL,
    moderation_closes_at DATETIME(6) NOT NULL,
    game_duration INT NOT NULL DEFAULT 10,
    profile_preparation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    settings_version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_event_singleton CHECK (id = 1),
    CONSTRAINT ck_event_times CHECK (opens_at < submit_closes_at AND submit_closes_at <= moderation_closes_at),
    CONSTRAINT ck_event_positive CHECK (game_duration > 0 AND settings_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE consent_policies (
    policy_type VARCHAR(30) NOT NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    PRIMARY KEY (policy_type, policy_version),
    CONSTRAINT ck_policy_type CHECK (policy_type IN ('PARTICIPATION', 'PROFILE_PUBLICATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE current_consent_policies (
    policy_type VARCHAR(30) NOT NULL PRIMARY KEY,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT fk_current_policy FOREIGN KEY (policy_type, policy_version)
        REFERENCES consent_policies (policy_type, policy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_consents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    policy_type VARCHAR(30) NOT NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    agreed_at DATETIME(6) NOT NULL,
    withdrawn_at DATETIME(6) NULL,
    CONSTRAINT uk_user_consent UNIQUE (user_id, policy_type, policy_version),
    CONSTRAINT fk_consent_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_consent_policy FOREIGN KEY (policy_type, policy_version)
        REFERENCES consent_policies(policy_type, policy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Append-only agreement/withdrawal history; user_consents is the current projection.
CREATE TABLE user_consent_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    consent_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_consent_event FOREIGN KEY (consent_id) REFERENCES user_consents(id),
    CONSTRAINT ck_consent_action CHECK (action IN ('AGREED', 'WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE profiles ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_profile_version CHECK (version > 0);

CREATE TABLE submission_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_kind VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    received_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_receipt_source UNIQUE (user_id, source_kind, source_id),
    CONSTRAINT uk_receipt_owner UNIQUE (id, user_id, source_kind, source_id),
    CONSTRAINT fk_receipt_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_receipt_kind CHECK (source_kind IN ('PROFILE', 'TAG_UNLOCK', 'SYNC_RESULT', 'WISH_REVISION', 'GAME_ATTEMPT', 'CHEER_REVISION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mission_completion_evidence (
    user_id BIGINT NOT NULL,
    mission_id BIGINT NOT NULL,
    source_kind VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    receipt_order BIGINT NOT NULL,
    accepted_received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, mission_id),
    CONSTRAINT uk_evidence_receipt UNIQUE (receipt_order),
    CONSTRAINT fk_evidence_progress FOREIGN KEY (user_id, mission_id) REFERENCES mission_progress(user_id, mission_id),
    CONSTRAINT fk_evidence_receipt FOREIGN KEY (receipt_order, user_id, source_kind, source_id)
        REFERENCES submission_receipts(id, user_id, source_kind, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Categories only; no participants, event dates, consent text or live tags are seeded.
INSERT INTO interests (name) VALUES ('백엔드'), ('프론트엔드'), ('AI'), ('데이터'), ('클라우드'), ('게임'), ('디자인')
    ON DUPLICATE KEY UPDATE name = VALUES(name);
