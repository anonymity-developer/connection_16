-- One slot per user/kind. Bodies and presentation are append-only revisions.
CREATE TABLE content_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    kind VARCHAR(10) NOT NULL,
    visibility_status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE',
    approved_revision_id BIGINT NULL,
    pending_revision_id BIGINT NULL,
    luck_score INT NULL,
    landing_zone INT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_content_slot(author_id,kind),
    UNIQUE KEY uk_content_owner(id,author_id),
    CONSTRAINT fk_content_author FOREIGN KEY(author_id) REFERENCES users(id),
    CONSTRAINT ck_content_kind CHECK(kind IN ('WISH','CHEER')),
    CONSTRAINT ck_content_visibility CHECK(visibility_status IN ('VISIBLE','HIDDEN')),
    CONSTRAINT ck_content_version CHECK(version > 0),
    CONSTRAINT ck_content_luck CHECK((luck_score IS NULL AND landing_zone IS NULL) OR (luck_score IS NOT NULL AND landing_zone >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE content_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    content_text VARCHAR(200) NOT NULL,
    template_id VARCHAR(10) NOT NULL,
    style VARCHAR(10) NOT NULL,
    anonymous BOOLEAN NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    reason_code VARCHAR(40) NULL,
    idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uk_revision_number(item_id,revision_no),
    UNIQUE KEY uk_revision_pointer(id,item_id),
    UNIQUE KEY uk_content_request(item_id,idempotency_key),
    CONSTRAINT fk_revision_item FOREIGN KEY(item_id) REFERENCES content_items(id),
    CONSTRAINT ck_revision_state CHECK(moderation_status IN ('QUEUED','PROCESSING','APPROVED','REJECTED','REVIEW_REQUIRED','SUPERSEDED')),
    CONSTRAINT ck_revision_presentation CHECK(template_id IN ('PLAIN','NOTE') AND style IN ('ROSE','INK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE content_items
    ADD CONSTRAINT fk_content_approved FOREIGN KEY(approved_revision_id,id) REFERENCES content_revisions(id,item_id),
    ADD CONSTRAINT fk_content_pending FOREIGN KEY(pending_revision_id,id) REFERENCES content_revisions(id,item_id);
CREATE TABLE moderation_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    revision_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    attempts INT NOT NULL DEFAULT 0,
    fence BIGINT NOT NULL DEFAULT 0,
    next_run_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_until DATETIME(6) NULL,
    provider VARCHAR(40) NULL,
    policy_version VARCHAR(64) NULL,
    last_error_code VARCHAR(40) NULL,
    UNIQUE KEY uk_moderation_revision(revision_id),
    INDEX ix_moderation_claim(status,next_run_at,lease_until,id),
    CONSTRAINT fk_job_revision FOREIGN KEY(revision_id) REFERENCES content_revisions(id),
    CONSTRAINT ck_job_status CHECK(status IN ('QUEUED','PROCESSING','APPROVED','REJECTED','REVIEW_REQUIRED','SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE moderation_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    fence BIGINT NOT NULL,
    reviewer_id BIGINT NULL,
    decision VARCHAR(20) NOT NULL,
    reason VARCHAR(300) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_review_fence(job_id,fence),
    CONSTRAINT fk_review_job FOREIGN KEY(job_id) REFERENCES moderation_jobs(id),
    CONSTRAINT fk_review_admin FOREIGN KEY(reviewer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Optional, operator-reviewed wish luck policy. No policy or live content is seeded.
CREATE TABLE content_settings (
    id TINYINT NOT NULL PRIMARY KEY,
    wish_luck_min INT NOT NULL,
    wish_luck_max INT NOT NULL,
    wish_zone_count INT NOT NULL,
    CONSTRAINT ck_content_settings CHECK(id=1 AND wish_luck_min <= wish_luck_max AND wish_luck_min >= 0 AND wish_luck_max <= 10000 AND wish_zone_count BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
