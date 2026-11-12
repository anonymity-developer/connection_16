ALTER TABLE content_revisions MODIFY content_text VARCHAR(300) NOT NULL;
UPDATE missions SET hint=CASE code
  WHEN 'SHOW_ME' THEN '1층 안내와 미션 공지를 확인해 주세요.'
  WHEN 'SYNC_ME' THEN '1층에서 S를 찾아 NFC 태그를 읽어주세요.'
  WHEN 'MAKE_A_WISH' THEN '10층에서 A를 찾아 NFC 태그를 읽어주세요.'
  WHEN 'FAST_TAP' THEN '20층에서 F를 찾아 NFC 태그를 읽어주세요.'
  WHEN 'SAY_SOMETHING' THEN '19층에서 Y를 찾아 NFC 태그를 읽어주세요.'
  ELSE hint END;

-- All valid finishes and participant resets serialize before changing the active TAP set.
CREATE TABLE tap_record_lock (
    id TINYINT NOT NULL PRIMARY KEY,
    CONSTRAINT ck_tap_record_lock CHECK(id=1)
) ENGINE=InnoDB;
INSERT INTO tap_record_lock VALUES(1);

CREATE TABLE tap_record_achievements (
    attempt_id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    previous_best INT NULL,
    achieved_at DATETIME(6) NOT NULL,
    message_text VARCHAR(100) NULL,
    message_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    message_at DATETIME(6) NULL,
    CONSTRAINT fk_tap_record_attempt FOREIGN KEY(attempt_id,user_id) REFERENCES tap_attempts(id,user_id) ON DELETE CASCADE,
    CONSTRAINT uk_tap_record_message UNIQUE(user_id,message_key),
    CONSTRAINT ck_tap_record_message CHECK(
      (message_text IS NULL AND message_key IS NULL AND message_at IS NULL)
      OR (message_text IS NOT NULL AND CHAR_LENGTH(message_text) BETWEEN 1 AND 100 AND message_key IS NOT NULL AND message_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_tap_active_score ON tap_attempts(status,archived_at,tap_count DESC,submitted_at,id);

-- Derive existing record breakers only from preserved, active, valid official submissions.
INSERT INTO tap_record_achievements(attempt_id,user_id,previous_best,achieved_at)
SELECT id,user_id,previous_best,submitted_at FROM (
  SELECT a.id,a.user_id,a.tap_count,a.submitted_at,
    MAX(a.tap_count) OVER(ORDER BY a.submitted_at,a.id ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING) AS previous_best
  FROM tap_attempts a WHERE a.archived_at IS NULL AND a.status='VALID' AND a.submitted_at IS NOT NULL AND a.tap_count IS NOT NULL
    AND EXISTS(SELECT 1 FROM mission_completion_evidence e WHERE e.user_id=a.user_id AND e.source_kind='GAME_ATTEMPT' AND e.source_id=a.id)
) records WHERE previous_best IS NULL OR tap_count>previous_best;
