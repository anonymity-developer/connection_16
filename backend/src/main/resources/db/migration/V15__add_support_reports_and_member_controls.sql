CREATE TABLE error_reports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    reporter_username VARCHAR(64) NULL,
    reporter_nickname VARCHAR(30) NULL,
    body VARCHAR(200) NOT NULL,
    page_path VARCHAR(160) NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_error_report_user FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT uk_error_report_request UNIQUE (reporter_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE game_participation_resets
    ADD COLUMN operation VARCHAR(24) NOT NULL DEFAULT 'RESET_GAMES',
    ADD COLUMN mission_code VARCHAR(30) NULL;

CREATE TABLE account_deactivations (
    user_id BIGINT NOT NULL PRIMARY KEY,
    original_mattermost_id VARCHAR(64) NOT NULL,
    original_username VARCHAR(64) NULL,
    change_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_deactivation_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_deactivation_change FOREIGN KEY (change_id) REFERENCES game_participation_resets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE show_me_regrant_blocks (
    user_id BIGINT NOT NULL PRIMARY KEY,
    CONSTRAINT fk_show_block_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE submission_receipts DROP CHECK ck_receipt_kind,
    ADD CONSTRAINT ck_receipt_kind CHECK(source_kind IN ('PROFILE','TAG_UNLOCK','SYNC_RESULT','WISH_REVISION','GAME_ATTEMPT','CHEER_REVISION','COMMENT_REVISION','ADMIN_ADJUSTMENT'));
ALTER TABLE admin_audit_logs DROP CHECK ck_audit_action,
    ADD CONSTRAINT ck_audit_action CHECK(action IN ('MODERATION_DECISION','MODERATION_RETRY','CONTENT_VISIBILITY','GAME_PARTICIPATION_RESET','MEMBER_DEACTIVATED','MISSION_ADJUSTED'));
