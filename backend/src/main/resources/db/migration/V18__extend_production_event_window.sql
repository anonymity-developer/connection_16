-- One-time production schedule correction requested on 2026-11-12 KST.
-- Match the deployed window exactly so local, fixture, and future schedules are untouched.
UPDATE event_settings
SET submit_closes_at = '2026-11-13 15:00:00.000000',
    moderation_closes_at = GREATEST(moderation_closes_at, '2026-11-13 15:00:00.000000'),
    settings_version = settings_version + 1
WHERE id = 1
  AND opens_at = '2026-11-11 00:50:29.206026'
  AND submit_closes_at = '2026-11-12 00:50:29.206026';
