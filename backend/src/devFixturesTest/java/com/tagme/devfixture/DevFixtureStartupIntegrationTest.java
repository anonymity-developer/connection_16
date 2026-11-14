package com.tagme.devfixture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@Testcontainers
@ActiveProfiles({"docker", "dev-fixtures"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "tagme.fixtures.project=tagme-fixtures", "tagme.fixtures.cheers=true", "tagme.content.worker-enabled=false",
        "tagme.auth.enabled=true", "tagme.auth.base-url=http://127.0.0.1",
        "tagme.auth.token=local-fixture-token", "tagme.auth.channel-id=fixturechannel000000000000",
        "tagme.auth.hmac-key=bG9jYWwtZml4dHVyZS1vbmx5LWhtYWMta2V5LTMyLWJ5dGVzLXBsdXM=",
        "server.servlet.session.cookie.secure=false"})
class DevFixtureStartupIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("tagme_fixtures");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
    @Autowired JdbcTemplate db;
    @Autowired DevFixtureSeeder seeder;

    @Test void seedsTheIsolatedStackAndRefusesLaterForeignParticipantsBeforeChangingData() {
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(29);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sync_results", Long.class)).isEqualTo(6);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tap_attempts WHERE status='VALID' AND archived_at IS NULL", Long.class)).isEqualTo(7);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tags", Long.class)).isEqualTo(4);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sync_results s JOIN users u ON u.id=s.user_id WHERE u.mattermost_username='test'", Long.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM content_items WHERE kind='CHEER' AND archived_at IS NULL", Long.class)).isEqualTo(28);
        assertThat(db.queryForObject("SELECT COUNT(DISTINCT style) FROM content_revisions WHERE moderation_status='APPROVED'", Long.class)).isEqualTo(10);
        assertThat(db.queryForObject("SELECT MAX(CHAR_LENGTH(content_text)) FROM content_revisions", Integer.class)).isLessThanOrEqualTo(400);
        seeder.run(new DefaultApplicationArguments());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM content_items WHERE kind='CHEER' AND archived_at IS NULL", Long.class)).isEqualTo(28);
        var settingsVersion = db.queryForObject("SELECT settings_version FROM event_settings WHERE id=1", Long.class);
        db.update("INSERT INTO users(mattermost_id,mattermost_username) VALUES ('non-fixture-identity','nonfixture')");
        assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non-fixture");
        assertThat(db.queryForObject("SELECT settings_version FROM event_settings WHERE id=1", Long.class)).isEqualTo(settingsVersion);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tags", Long.class)).isEqualTo(4);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(30);
    }
}
