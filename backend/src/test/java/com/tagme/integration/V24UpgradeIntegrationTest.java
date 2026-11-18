package com.tagme.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

/** Candidate-only verification: every DB is owned by a disposable MySQL container. */
@Tag("integration")
class V24UpgradeIntegrationTest {
    @TempDir Path temporary;
    private static final String FUNNEL="V23__add_mission_funnel_events.sql";
    private static final String PAGES="V24__add_page_event_analytics.sql";
    private MySQLContainer<?> mysql() {
        var mysql=new MySQLContainer<>("mysql:8.4");mysql.start();return mysql;
    }
    private DriverManagerDataSource source(MySQLContainer<?> mysql) {
        return new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
    }
    private Flyway migrate(DriverManagerDataSource source,String target) {
        var flyway=Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion(target)).load();
        flyway.migrate();flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo(target);
        return flyway;
    }
    private List<Map<String,Object>> history(JdbcTemplate db) {
        return db.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
    }
    private void funnel(JdbcTemplate db,String key) {
        db.update("INSERT INTO mission_funnel_events(user_id,mission,questionnaire_version,generation,event_key,event_type,step,occurred_at,received_at) VALUES (900,'SYNC_ME','old-version',0,?,'QUESTION_VIEW',1,'2000-01-01','2000-01-01')",key);
    }
    private void page(JdbcTemplate db,long user,String key) {
        db.update("INSERT INTO page_events(user_id,session_key,event_key,screen_id,event_type,source,event_at,recorded_at) VALUES (?,?,?,'HOME','PAGE_VIEW','CLIENT',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",user,"a".repeat(64),key);
    }
    private void structure(JdbcTemplate db) {
        assertThat(db.queryForList("SELECT script FROM flyway_schema_history WHERE version IN ('21','22','23','24') ORDER BY installed_rank",String.class))
            .containsExactly("V21__tap_recovery_evidence.sql","V22__system_tap_recovery.sql",FUNNEL,PAGES);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success=1",Integer.class)).isEqualTo(24);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM page_event_collection WHERE id=1 AND stored_events=0 AND started_at IS NOT NULL",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mission_funnel_events' AND column_name='analytics_session_key'",String.class)).isEqualTo("YES");
        assertThat(db.queryForList("SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='page_events' AND index_name='ix_page_event_session' ORDER BY seq_in_index",String.class))
            .containsExactly("user_id","session_key","recorded_at","id");
    }

    @Test void freshDatabaseMigratesV1ThroughV24AndEnforcesPageUniquenessAndForeignKey() {
        try(var mysql=mysql()) {
            var source=source(mysql);var db=new JdbcTemplate(source);
            migrate(source,"24");structure(db);
            db.update("INSERT INTO users(id,mattermost_id) VALUES (900,'isolated-v24-fresh')");
            page(db,900,"11111111-1111-1111-1111-111111111111");
            assertThatThrownBy(()->page(db,900,"11111111-1111-1111-1111-111111111111")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(()->page(db,901,"22222222-2222-2222-2222-222222222222")).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events",Integer.class)).isEqualTo(1);
        }
    }

    @Test void populatedV20UpgradesThroughEveryVersionPreservingRowsHistoryChecksumsAndLegacyFunnelWriter() {
        try(var mysql=mysql()) {
            var source=source(mysql);var db=new JdbcTemplate(source);
            migrate(source,"20");
            db.update("INSERT INTO users(id,mattermost_id) VALUES (900,'isolated-v24-upgrade')");
            db.update("INSERT INTO tap_attempts(id,user_id,generation,idempotency_key,starts_at,ends_at,submit_deadline,status,tap_count) VALUES (900,900,1,'isolated-seed','2000-01-01 00:00:00','2000-01-01 00:00:10','2000-01-01 00:00:30','INVALID',3)");
            var user=db.queryForMap("SELECT * FROM users WHERE id=900");
            var attempt=db.queryForMap("SELECT * FROM tap_attempts WHERE id=900");
            var history20=history(db);
            migrate(source,"21");
            db.update("INSERT INTO tap_recoveries(attempt_id,user_id,actor_id,request_key,expected_generation,reason,original_status,original_deadline,effective_deadline,recognized_at) VALUES (900,900,900,'isolated-recovery',0,'isolated migration evidence','INVALID','2000-01-01 00:00:30','2000-01-01 00:00:30','2000-01-01 00:00:10')");
            var recovery=db.queryForMap("SELECT * FROM tap_recoveries WHERE attempt_id=900");
            var history21=history(db);
            migrate(source,"22");var history22=history(db);
            migrate(source,"23");var history23=history(db);
            funnel(db,"33333333-3333-3333-3333-333333333333");
            var oldEvent=db.queryForMap("SELECT * FROM mission_funnel_events WHERE user_id=900");
            var funnelStart=db.queryForMap("SELECT * FROM funnel_collection WHERE id=1");
            var flyway=migrate(source,"24");structure(db);
            assertThat(db.queryForMap("SELECT * FROM users WHERE id=900")).isEqualTo(user);
            assertThat(db.queryForMap("SELECT * FROM tap_attempts WHERE id=900")).isEqualTo(attempt);
            assertThat(db.queryForMap("SELECT * FROM tap_recoveries WHERE attempt_id=900")).isEqualTo(recovery);
            assertThat(db.queryForMap("SELECT * FROM funnel_collection WHERE id=1")).isEqualTo(funnelStart);
            var after=db.queryForMap("SELECT * FROM mission_funnel_events WHERE user_id=900");
            assertThat(after.remove("analytics_session_key")).isNull();assertThat(after).isEqualTo(oldEvent);
            for(var prior:List.of(history20,history21,history22,history23))assertThat(history(db).subList(0,prior.size())).isEqualTo(prior);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM page_events",Integer.class)).isZero();
            funnel(db,"44444444-4444-4444-4444-444444444444");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_funnel_events WHERE analytics_session_key IS NULL",Integer.class)).isEqualTo(2);
            page(db,900,"55555555-5555-5555-5555-555555555555");
            assertThat(flyway.migrate().migrationsExecuted).isZero();flyway.validate();
        }
    }

    @Test void retainedOriginalFunnelV21AndPageV23HistoryRejectsCandidateWithoutChangingDataOrChecksums() throws Exception {
        try(var mysql=mysql()) {
            var source=source(mysql);var db=new JdbcTemplate(source);
            var resources=Path.of(getClass().getClassLoader().getResource("db/migration").toURI());
            var legacy=Files.createDirectory(temporary.resolve("original-p0"));
            try(var files=Files.list(resources)) {
                for(var file:files.toList()) {
                    String name=file.getFileName().toString();
                    if(name.startsWith("V") && Integer.parseInt(name.substring(1,name.indexOf("__")))>20)continue;
                    Files.copy(file,legacy.resolve(name));
                }
            }
            Files.copy(resources.resolve(FUNNEL),legacy.resolve("V21__add_mission_funnel_events.sql"));
            Files.copy(resources.resolve(PAGES),legacy.resolve("V23__add_page_event_analytics.sql"));
            var original=Flyway.configure().dataSource(source).locations("filesystem:"+legacy).load();
            original.migrate();original.validate();
            db.update("INSERT INTO users(id,mattermost_id) VALUES (900,'isolated-original-p0')");
            funnel(db,"66666666-6666-6666-6666-666666666666");
            page(db,900,"77777777-7777-7777-7777-777777777777");
            var beforeHistory=history(db);var beforeFunnel=db.queryForList("SELECT * FROM mission_funnel_events");var beforePage=db.queryForList("SELECT * FROM page_events");
            var candidate=Flyway.configure().dataSource(source).load();
            assertThat(candidate.validateWithResult().validationSuccessful).isFalse();
            assertThatThrownBy(candidate::migrate).isInstanceOf(FlywayException.class);
            assertThat(history(db)).isEqualTo(beforeHistory);
            assertThat(db.queryForList("SELECT * FROM mission_funnel_events")).isEqualTo(beforeFunnel);
            assertThat(db.queryForList("SELECT * FROM page_events")).isEqualTo(beforePage);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='tap_recoveries'",Integer.class)).isZero();
        }
    }
}
