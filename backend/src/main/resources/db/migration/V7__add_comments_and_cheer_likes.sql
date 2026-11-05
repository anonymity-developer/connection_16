-- Keep the WISH/CHEER one-slot invariant while allowing multiple COMMENT items.
ALTER TABLE content_items DROP CHECK ck_content_kind,
    DROP INDEX uk_content_slot,
    ADD COLUMN slot_kind VARCHAR(10) GENERATED ALWAYS AS (CASE WHEN kind IN ('WISH','CHEER') THEN kind ELSE NULL END) STORED,
    ADD CONSTRAINT ck_content_kind CHECK(kind IN ('WISH','CHEER','COMMENT')),
    ADD UNIQUE KEY uk_content_slot(author_id,slot_kind),
    ADD UNIQUE KEY uk_content_id_kind(id,kind),
    ADD INDEX ix_content_kind_id(kind,id);
CREATE TABLE wish_comments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wish_id BIGINT NOT NULL,
    wish_kind VARCHAR(10) NOT NULL DEFAULT 'WISH',
    content_item_id BIGINT NOT NULL,
    item_kind VARCHAR(10) NOT NULL DEFAULT 'COMMENT',
    author_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uk_comment_item(content_item_id),
    UNIQUE KEY uk_comment_request(author_id,idempotency_key),
    INDEX ix_comment_wish(wish_id,id),
    INDEX ix_comment_author_wish(author_id,wish_id,id),
    CONSTRAINT ck_comment_kinds CHECK(wish_kind='WISH' AND item_kind='COMMENT'),
    CONSTRAINT fk_comment_wish FOREIGN KEY(wish_id,wish_kind) REFERENCES content_items(id,kind),
    CONSTRAINT fk_comment_kind FOREIGN KEY(content_item_id,item_kind) REFERENCES content_items(id,kind),
    CONSTRAINT fk_comment_owner FOREIGN KEY(content_item_id,author_id) REFERENCES content_items(id,author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE cheer_likes (
    card_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    card_kind VARCHAR(10) NOT NULL DEFAULT 'CHEER',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY(card_id,user_id),
    CONSTRAINT ck_like_kind CHECK(card_kind='CHEER'),
    CONSTRAINT fk_like_card FOREIGN KEY(card_id,card_kind) REFERENCES content_items(id,kind),
    CONSTRAINT fk_like_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE submission_receipts DROP CHECK ck_receipt_kind,
    ADD CONSTRAINT ck_receipt_kind CHECK(source_kind IN ('PROFILE','TAG_UNLOCK','SYNC_RESULT','WISH_REVISION','GAME_ATTEMPT','CHEER_REVISION','COMMENT_REVISION'));
