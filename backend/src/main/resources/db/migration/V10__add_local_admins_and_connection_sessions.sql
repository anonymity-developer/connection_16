-- Local administrators share session/role enforcement, never Mattermost identities or participant profiles.
CREATE TABLE local_admin_accounts (
    user_id BIGINT NOT NULL PRIMARY KEY,
    login_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    CONSTRAINT fk_local_admin_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- An opaque, randomly generated correlation ID, not a browser/Redis session credential.
CREATE TABLE connection_sessions (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    credential_version BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    end_reason VARCHAR(16) NULL,
    CONSTRAINT fk_connection_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_connection_times CHECK(expires_at >= started_at AND (ended_at IS NULL OR ended_at >= started_at)),
    CONSTRAINT ck_connection_reason CHECK(end_reason IS NULL OR end_reason IN ('LOGOUT','EXPIRED','REPLACED','REVOKED')),
    INDEX ix_connection_user_time(user_id,started_at),
    INDEX ix_connection_active(ended_at,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
