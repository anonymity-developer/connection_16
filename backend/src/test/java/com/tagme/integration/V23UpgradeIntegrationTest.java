package com.tagme.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration") @Testcontainers
class V23UpgradeIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Test void populatedV21UpgradesWithoutRewritingLegacyRowsAndOldWriterStillWorks() {
        var source=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("21")).load().migrate();
        var db=new JdbcTemplate(source);
        db.update("INSERT INTO users(id,mattermost_id) VALUES (900,'isolated-upgrade-fixture')");
        String insert="INSERT INTO mission_funnel_events(user_id,mission,questionnaire_version,generation,event_key,event_type,step,occurred_at,received_at) VALUES (900,'SYNC_ME','old-version',0,?,'QUESTION_VIEW',1,'2000-01-01','2000-01-01')";
        db.update(insert,"11111111-1111-1111-1111-111111111111");
        var before=db.queryForMap("SELECT * FROM mission_funnel_events WHERE user_id=900");
        var flyway=Flyway.configure().dataSource(source).load();flyway.migrate();flyway.validate();
        var after=db.queryForMap("SELECT * FROM mission_funnel_events WHERE user_id=900");
        assertThat(after.remove("analytics_session_key")).isNull();assertThat(after).isEqualTo(before);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events",Integer.class)).isZero();
        db.update(insert,"22222222-2222-2222-2222-222222222222");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_funnel_events WHERE analytics_session_key IS NULL",Integer.class)).isEqualTo(2);
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("23");
    }
}
