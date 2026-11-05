-- Identity fields are imported after verification; no live accounts or photos are seeded.
ALTER TABLE users
    ADD COLUMN mattermost_profile_synced_at DATETIME(6) NULL,
    ADD COLUMN mattermost_picture_updated_at BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_user_picture_version CHECK(mattermost_picture_updated_at >= 0);
ALTER TABLE interests
    ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'DOMAIN',
    ADD CONSTRAINT ck_interest_category CHECK(category IN ('DOMAIN','TECH_STACK'));
INSERT INTO interests(name,category) VALUES
    ('Java','TECH_STACK'),('Spring Boot','TECH_STACK'),('Python','TECH_STACK'),
    ('FastAPI','TECH_STACK'),('Django','TECH_STACK'),('JavaScript','TECH_STACK'),
    ('TypeScript','TECH_STACK'),('React','TECH_STACK'),('Vue','TECH_STACK'),
    ('Next.js','TECH_STACK'),('MySQL','TECH_STACK'),('PostgreSQL','TECH_STACK'),
    ('Redis','TECH_STACK'),('Docker','TECH_STACK'),('Kubernetes','TECH_STACK'),
    ('AWS','TECH_STACK'),('Flutter','TECH_STACK'),('Kotlin','TECH_STACK'),
    ('Swift','TECH_STACK'),('C++','TECH_STACK'),('PyTorch','TECH_STACK')
ON DUPLICATE KEY UPDATE category=VALUES(category);
-- Legacy is_public stays internal for compatibility; participant profile saves always publish.
