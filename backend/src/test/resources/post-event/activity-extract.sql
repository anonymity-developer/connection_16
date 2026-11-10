-- DESIGN / ISOLATED TEST ONLY. No runtime route or real-data CLI executes this query.
-- Named parameters are UTC instants, half-open [from_at, until_at), until_at <= observed_at.
-- internal_user_id and record_key are staging-only and MUST NOT be written to approved CSV.
WITH cohort AS (
  SELECT id FROM users WHERE role='USER' AND status='ACTIVE' AND created_at<=:observed_at
), events AS (
  SELECT u.id internal_user_id,'ACCOUNT_CREATED' event_type,NULL mission_code,u.created_at event_time,u.status status,'users.created_at' source_type,CONCAT('user:',u.id) record_key
    FROM users u JOIN cohort c ON c.id=u.id
  UNION ALL SELECT p.user_id,'PROFILE_FIRST_PUBLISHED','SHOW_ME',p.published_at,IF(p.is_public,'PUBLIC','PRIVATE'),'profiles.published_at',CONCAT('profile:',p.user_id)
    FROM profiles p JOIN cohort c ON c.id=p.user_id WHERE p.published_at IS NOT NULL
  UNION ALL SELECT p.user_id,'NFC_FIRST_UNLOCK',m.code,p.unlocked_at,p.status,'mission_progress.unlocked_at',CONCAT('unlock:',p.user_id,':',m.id)
    FROM mission_progress p JOIN missions m ON m.id=p.mission_id JOIN cohort c ON c.id=p.user_id WHERE m.code<>'SHOW_ME'
  UNION ALL SELECT s.user_id,'SYNC_SUBMITTED','SYNC_ME',s.submitted_at,'STORED','sync_results.submitted_at',CONCAT('sync:',s.user_id)
    FROM sync_results s JOIN cohort c ON c.id=s.user_id
  UNION ALL SELECT i.author_id,CASE i.kind WHEN 'WISH' THEN 'WISH_SUBMITTED' WHEN 'CHEER' THEN 'CHEER_SUBMITTED' ELSE 'COMMENT_SUBMITTED' END,
    CASE i.kind WHEN 'WISH' THEN 'MAKE_A_WISH' WHEN 'CHEER' THEN 'SAY_SOMETHING' ELSE NULL END,r.submitted_at,r.moderation_status,'content_revisions.submitted_at',CONCAT('revision:',r.id)
    FROM content_revisions r JOIN content_items i ON i.id=r.item_id JOIN cohort c ON c.id=i.author_id
  UNION ALL SELECT i.author_id,'MODERATION_DECISION',CASE i.kind WHEN 'WISH' THEN 'MAKE_A_WISH' WHEN 'CHEER' THEN 'SAY_SOMETHING' ELSE NULL END,
    v.created_at,v.decision,'moderation_reviews.created_at',CONCAT('review:',v.id)
    FROM moderation_reviews v JOIN moderation_jobs j ON j.id=v.job_id JOIN content_revisions r ON r.id=j.revision_id JOIN content_items i ON i.id=r.item_id JOIN cohort c ON c.id=i.author_id
  UNION ALL SELECT t.user_id,'TAP_SCHEDULED','FAST_TAP',t.starts_at,t.status,'tap_attempts.starts_at',CONCAT('tap-start:',t.id)
    FROM tap_attempts t JOIN cohort c ON c.id=t.user_id
  UNION ALL SELECT t.user_id,'TAP_SUBMITTED','FAST_TAP',t.submitted_at,t.status,'tap_attempts.submitted_at',CONCAT('tap-submit:',t.id)
    FROM tap_attempts t JOIN cohort c ON c.id=t.user_id WHERE t.submitted_at IS NOT NULL
  UNION ALL SELECT p.user_id,'LETTER_GRANTED',m.code,p.completed_at,p.status,'mission_progress.completed_at',CONCAT('grant:',p.user_id,':',m.id)
    FROM mission_progress p JOIN missions m ON m.id=p.mission_id JOIN cohort c ON c.id=p.user_id WHERE p.status='COMPLETED' AND p.completed_at IS NOT NULL
  UNION ALL SELECT e.user_id,'ALL_COMPLETED',NULL,e.completed_at,'RECORDED','event_completions.completed_at',CONCAT('complete:',e.user_id)
    FROM event_completions e JOIN cohort c ON c.id=e.user_id
  UNION ALL SELECT h.user_id,'HEART_CURRENT', 'SAY_SOMETHING',h.created_at,'PRESENT','cheer_likes.created_at',CONCAT('heart:',h.card_id,':',h.user_id)
    FROM cheer_likes h JOIN cohort c ON c.id=h.user_id
  UNION ALL SELECT v.viewer_id,'PROFILE_FIRST_VISIT',NULL,v.first_viewed_at,'RECORDED','profile_views.first_viewed_at',CONCAT('visit:',v.viewer_id,':',v.viewed_id)
    FROM profile_views v JOIN cohort c ON c.id=v.viewer_id
)
SELECT internal_user_id,event_type,mission_code,event_time,status,source_type,record_key FROM events
WHERE event_time>=:from_at AND event_time<:until_at AND event_time<=:observed_at
ORDER BY event_time,internal_user_id,event_type,record_key
