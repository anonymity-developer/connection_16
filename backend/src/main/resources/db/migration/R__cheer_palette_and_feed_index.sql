-- Existing revisions, including NOTE and the original colours, remain immutable.
ALTER TABLE content_revisions DROP CHECK ck_revision_presentation,
    ADD CONSTRAINT ck_revision_presentation CHECK(template_id IN ('PLAIN','NOTE') AND style IN ('ROSE','INK','PEACH','LEMON','MINT','SAGE','LAVENDER','LILAC','SAND','CLOUD'));
-- Repeatable to keep this independent of the unmerged FAST TAP V20 migration.
SET @cheer_index_sql = (SELECT IF(COUNT(*)=0,
  'CREATE INDEX ix_content_public_feed ON content_items(kind,visibility_status,archived_at,id)',
  'SELECT 1') FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='content_items' AND index_name='ix_content_public_feed');
PREPARE cheer_index_statement FROM @cheer_index_sql;
EXECUTE cheer_index_statement;
DEALLOCATE PREPARE cheer_index_statement;
