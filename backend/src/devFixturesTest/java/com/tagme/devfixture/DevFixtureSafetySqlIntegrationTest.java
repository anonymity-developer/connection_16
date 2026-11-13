package com.tagme.devfixture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@Testcontainers
class DevFixtureSafetySqlIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("tagme_fixtures");

    @Test void realMysqlRejectsForeignIdentitiesAndAllowsOnlySyntheticParticipants() {
        var db = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        db.execute("CREATE TABLE users (mattermost_id VARCHAR(64),role VARCHAR(16),status VARCHAR(16))");
        db.update("INSERT INTO users VALUES ('devfixture0000000000000001','USER','ACTIVE'),('local-admin:test','ADMIN','ACTIVE'),('archived:3','USER','WITHDRAWN')");
        var environment = new MockEnvironment().withProperty("tagme.fixtures.project", "tagme-fixtures");
        environment.setActiveProfiles("docker", "dev-fixtures");
        var safety = new DevFixtureSafety(environment, db);
        assertThatCode(safety::verify).doesNotThrowAnyException();
        for (String identity : new String[]{"real-external-identity", "devfixture-not-a-synthetic-id", null}) {
            db.update("INSERT INTO users VALUES (?,'USER','ACTIVE')", identity);
            assertThatThrownBy(safety::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("non-fixture");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(4);
            db.update("DELETE FROM users WHERE role='USER' AND status='ACTIVE' AND NOT COALESCE(mattermost_id REGEXP '^devfixture[0-9]{16}$',FALSE)");
        }
        assertThatCode(safety::verify).doesNotThrowAnyException();
    }
}
