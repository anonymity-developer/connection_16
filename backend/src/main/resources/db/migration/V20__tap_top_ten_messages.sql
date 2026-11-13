-- Keep record-breaking history separate from durable top-ten message eligibility.
CREATE TABLE tap_message_permissions (
    attempt_id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    message_text VARCHAR(100) NULL,
    message_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    message_at DATETIME(6) NULL,
    CONSTRAINT fk_tap_message_attempt FOREIGN KEY(attempt_id,user_id) REFERENCES tap_attempts(id,user_id) ON DELETE CASCADE,
    CONSTRAINT uk_tap_message_key UNIQUE(user_id,message_key),
    CONSTRAINT ck_tap_message_fields CHECK(
      (message_text IS NULL AND message_key IS NULL AND message_at IS NULL)
      OR (message_text IS NOT NULL AND CHAR_LENGTH(message_text) BETWEEN 1 AND 100 AND message_key IS NOT NULL AND message_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Preserve existing permissions, archived messages and messages longer than the new write limit.
INSERT INTO tap_message_permissions(attempt_id,user_id,granted_at,message_text,message_key,message_at)
SELECT attempt_id,user_id,achieved_at,message_text,message_key,message_at FROM tap_record_achievements;
