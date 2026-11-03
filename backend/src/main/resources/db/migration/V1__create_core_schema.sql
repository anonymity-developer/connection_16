CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mattermost_id VARCHAR(64) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    region VARCHAR(30) NOT NULL,
    class_no INT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_login_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_mattermost UNIQUE (mattermost_id),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT ck_users_class CHECK (class_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE profiles (
    user_id BIGINT NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    image_url VARCHAR(2048) NULL,
    bio VARCHAR(200) NULL,
    github_url VARCHAR(2048) NULL,
    blog_url VARCHAR(2048) NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    published_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX ix_profiles_public_user (is_public, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_interests_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_interests (
    user_id BIGINT NOT NULL,
    interest_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, interest_id),
    CONSTRAINT fk_user_interests_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_interests_interest FOREIGN KEY (interest_id) REFERENCES interests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE missions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    letter CHAR(1) NOT NULL,
    title VARCHAR(50) NOT NULL,
    hint VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_missions_code UNIQUE (code),
    CONSTRAINT uk_missions_sort_order UNIQUE (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mission_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_tags_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_tags_mission FOREIGN KEY (mission_id) REFERENCES missions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mission_progress (
    user_id BIGINT NOT NULL,
    mission_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNLOCKED',
    unlocked_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (user_id, mission_id),
    CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_progress_mission FOREIGN KEY (mission_id) REFERENCES missions (id),
    CONSTRAINT ck_progress_status CHECK (status IN ('UNLOCKED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_progress_completed CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    INDEX ix_progress_mission_status (mission_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Singleton row locked only when allocating a new operational completion number.
CREATE TABLE event_completion_sequence (
    id TINYINT NOT NULL,
    next_value BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_completion_sequence_id CHECK (id = 1),
    CONSTRAINT ck_completion_sequence_positive CHECK (next_value > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE event_completions (
    user_id BIGINT NOT NULL,
    completion_order BIGINT NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_completion_order UNIQUE (completion_order),
    CONSTRAINT fk_completion_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE profile_views (
    viewer_id BIGINT NOT NULL,
    viewed_id BIGINT NOT NULL,
    first_viewed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (viewer_id, viewed_id),
    CONSTRAINT fk_views_viewer FOREIGN KEY (viewer_id) REFERENCES users (id),
    CONSTRAINT fk_views_viewed FOREIGN KEY (viewed_id) REFERENCES users (id),
    CONSTRAINT ck_views_not_self CHECK (viewer_id <> viewed_id),
    INDEX ix_views_inbound (viewed_id, viewer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
