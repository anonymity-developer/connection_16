-- Future operator use only. Work does not execute this against any database.
-- Configuration metadata only; never selects users, credentials, OTP, tag hashes or raw tag URLs.
SET SESSION time_zone = '+00:00';
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT(
 'capturedAt',DATE_FORMAT(UTC_TIMESTAMP(6),'%Y-%m-%dT%H:%i:%s.%fZ'),
 'event',(SELECT JSON_OBJECT(
   'opensAt',DATE_FORMAT(opens_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
   'submitClosesAt',DATE_FORMAT(submit_closes_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
   'moderationClosesAt',DATE_FORMAT(moderation_closes_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
   'gameDuration',game_duration,'settingsVersion',settings_version,'profilePreparationEnabled',profile_preparation_enabled)
   FROM event_settings WHERE id=1),
 'policies',COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('type',c.policy_type,'version',c.policy_version,
   'titleChars',CHAR_LENGTH(REGEXP_REPLACE(p.title,'[[:space:]]','')),'bodyChars',CHAR_LENGTH(REGEXP_REPLACE(p.body,'[[:space:]]',''))))
   FROM current_consent_policies c LEFT JOIN consent_policies p ON p.policy_type=c.policy_type AND p.policy_version=c.policy_version),JSON_ARRAY()),
 'activeMissions',(SELECT COUNT(*) FROM missions WHERE active=TRUE),
 'missions',(SELECT JSON_ARRAYAGG(JSON_OBJECT('code',code,'active',active)) FROM missions),
 'nfc',(SELECT JSON_ARRAYAGG(JSON_OBJECT('missionCode',code,'activeCount',amount)) FROM (
   SELECT m.code,COUNT(t.id) amount FROM missions m LEFT JOIN tags t ON t.mission_id=m.id AND t.active=TRUE
   WHERE m.code IN ('SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING') GROUP BY m.code) n),
 'nfcIntegrity',JSON_OBJECT(
   'invalidDigestCount',(SELECT COUNT(*) FROM tags WHERE active=TRUE AND token_hash NOT REGEXP '^[0-9a-f]{64}$'),
   'duplicateDigestCount',(SELECT COUNT(*)-COUNT(DISTINCT token_hash) FROM tags)),
 'currentQuestionnaires',(SELECT COUNT(*) FROM current_sync_questionnaire WHERE id=1),
 'questionnaire',(SELECT JSON_OBJECT('version',c.version,'definition',q.definition_json) FROM current_sync_questionnaire c JOIN sync_questionnaires q ON q.version=c.version WHERE c.id=1),
 'contentSettings',(SELECT COUNT(*) FROM content_settings WHERE id=1)
) AS opening_snapshot;
ROLLBACK;
