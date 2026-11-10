-- READ ONLY: no user rows, policy bodies, passwords, OTPs or tag tokens selected.
SET SESSION time_zone = '+00:00';
START TRANSACTION READ ONLY;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('event_settings','consent_policies','current_consent_policies')
ORDER BY TABLE_NAME,ORDINAL_POSITION;
SELECT JSON_OBJECT(
 'event',(SELECT JSON_OBJECT('opensAt',DATE_FORMAT(opens_at,'%Y-%m-%dT%H:%i:%sZ'),'submitClosesAt',DATE_FORMAT(submit_closes_at,'%Y-%m-%dT%H:%i:%sZ'),'moderationClosesAt',DATE_FORMAT(moderation_closes_at,'%Y-%m-%dT%H:%i:%sZ'),'gameDuration',game_duration,'settingsVersion',settings_version,'profilePreparationEnabled',profile_preparation_enabled) FROM event_settings WHERE id=1),
 'policies',COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('type',c.policy_type,'version',c.policy_version,'titleChars',CHAR_LENGTH(TRIM(p.title)),'bodyChars',CHAR_LENGTH(TRIM(p.body)))) FROM current_consent_policies c LEFT JOIN consent_policies p ON p.policy_type=c.policy_type AND p.policy_version=c.policy_version),JSON_ARRAY()),
 'activeMissions',(SELECT COUNT(*) FROM missions WHERE active=TRUE),
 'currentQuestionnaires',(SELECT COUNT(*) FROM current_sync_questionnaire WHERE id=1),
 'contentSettings',(SELECT COUNT(*) FROM content_settings WHERE id=1)
) AS readiness_snapshot;
ROLLBACK;
