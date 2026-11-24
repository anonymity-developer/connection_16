-- V21 remains immutable. Existing SYNC events are read in place, never copied.
ALTER TABLE mission_funnel_events
    ADD COLUMN analytics_session_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;

CREATE TABLE page_event_collection (
    id INT NOT NULL PRIMARY KEY,
    started_at DATETIME(6) NOT NULL,
    stored_events BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_page_collection CHECK (id=1 AND stored_events>=0)
) ENGINE=InnoDB;
INSERT INTO page_event_collection(id,started_at) VALUES (1,UTC_TIMESTAMP(6));

CREATE TABLE page_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_page_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    screen_id VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mission VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    step VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    source VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_page_event_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_page_event UNIQUE (user_id,source,event_key),
    CONSTRAINT ck_page_event_source CHECK (
        (source='CLIENT' AND event_type IN ('PAGE_VIEW','MISSION_STEP_VIEW') AND session_key IS NOT NULL)
        OR (source='SERVER' AND event_type='MISSION_SUBMIT_SUCCESS' AND mission IS NOT NULL)),
    CONSTRAINT ck_page_event_mission CHECK (mission IS NULL OR mission IN ('SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING')),
    CONSTRAINT ck_page_event_screen CHECK (screen_id IN (
        'HOME','MISSION_OVERVIEW','SHOW_ME','PROFILE','SYNC_ME','WISH','WISH_LIST','WISH_DETAIL','FAST_TAP','CHEER',
        'DISCOVERY_RECOMMENDED','DISCOVERY_PEOPLE','DISCOVERY_GRAPH','DISCOVERY_SELECTION','PROFILE_DETAIL','ACCOUNT','LOGIN','SIGNUP',
        'PASSWORD_RESET','PASSWORD_CHANGE','SIGNUP_CODE','RESET_CODE','CHANGE_CODE','NFC_TAG','COLLECTION_COMPLETE',
        'NOT_FOUND','ERROR_REPORT','MISSION_REWARD','COLLECTION_REWARD')),
    INDEX ix_page_event_period (recorded_at,id),
    INDEX ix_page_event_screen (screen_id,event_type,recorded_at,id),
    INDEX ix_page_event_session (user_id,session_key,recorded_at,id)
) ENGINE=InnoDB;
