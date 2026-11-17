-- Additive telemetry only. Never backfill historical views or alter mission state.
CREATE TABLE funnel_collection (
    id INT NOT NULL PRIMARY KEY,
    started_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_funnel_collection CHECK (id = 1)
) ENGINE=InnoDB;
INSERT INTO funnel_collection VALUES (1, UTC_TIMESTAMP(6));

CREATE TABLE mission_funnel_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mission VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    questionnaire_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    generation BIGINT NOT NULL,
    event_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    step INT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_funnel_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_funnel_event UNIQUE (user_id, event_key),
    CONSTRAINT ck_funnel_mission CHECK (mission = 'SYNC_ME'),
    CONSTRAINT ck_funnel_generation CHECK (generation >= 0),
    CONSTRAINT ck_funnel_step CHECK ((event_type = 'SCREEN_ENTER' AND step = 0) OR (event_type = 'QUESTION_VIEW' AND step BETWEEN 1 AND 20)),
    INDEX ix_funnel_aggregate (questionnaire_version, step, user_id, generation)
) ENGINE=InnoDB;
