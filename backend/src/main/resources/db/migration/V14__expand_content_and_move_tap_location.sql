ALTER TABLE content_revisions MODIFY content_text VARCHAR(400) NOT NULL;

UPDATE missions
SET hint='19층과 20층 사이 계단에서 F를 찾아 NFC 태그를 읽어주세요.'
WHERE code='FAST_TAP';
