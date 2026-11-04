-- Identity remains bound to the immutable external ID. Existing rows receive
-- neither an invented username nor a shared password. Onboarding fills metadata.
ALTER TABLE users
    ADD COLUMN mattermost_username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    ADD CONSTRAINT uk_users_username UNIQUE (mattermost_username),
    MODIFY real_name VARCHAR(100) NULL,
    MODIFY region VARCHAR(30) NULL,
    MODIFY class_no INT NULL;

CREATE TABLE user_credentials (
    user_id BIGINT NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_updated_at DATETIME(6) NOT NULL,
    credential_version BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_credentials_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_credentials_version CHECK (credential_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
