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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

/** Isolated candidate verification only; no environment URL or persistent DB is used. */
@Tag("integration")
class FunnelV23MigrationIntegrationTest {
    @TempDir Path temporary;
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final String FUNNEL = "V23__add_mission_funnel_events.sql";

    private MySQLContainer<?> mysql() {
        var container = new MySQLContainer<>("mysql:8.4");
        container.start();
        return container;
    }
    private DriverManagerDataSource source(MySQLContainer<?> mysql) {
        return new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }
    private Flyway flyway(DriverManagerDataSource source, String target, String location) {
        var configuration = Flyway.configure().dataSource(source).locations(location);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        return configuration.load();
    }
    private void migrate(DriverManagerDataSource source, String target) {
        var flyway = flyway(source, target, MIGRATIONS);
        flyway.migrate();
        flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo(target);
    }
    private List<Map<String, Object>> history(JdbcTemplate db) {
        return db.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
    }
    private int tables(JdbcTemplate db, String names) {
        return db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN (" + names + ")", Integer.class);
    }
    private void event(JdbcTemplate db, long user, String key, long generation, int step) {
        db.update("INSERT INTO mission_funnel_events(user_id,mission,questionnaire_version,generation,event_key,event_type,step,occurred_at,received_at) VALUES (?,'SYNC_ME','isolated-v23',?,?,'QUESTION_VIEW',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", user, generation, key, step);
    }
    private void rejectsCheck(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String constraint) {
        var failure = catchThrowable(action);
        assertThat(failure).isInstanceOf(DataAccessException.class);
        assertThat(failure.getCause()).isInstanceOf(java.sql.SQLException.class).hasMessageContaining(constraint);
        assertThat(((java.sql.SQLException) failure.getCause()).getErrorCode()).isEqualTo(3819);
    }
    private void structure(JdbcTemplate db) {
        assertThat(tables(db, "'tap_recoveries','funnel_collection','mission_funnel_events'")).isEqualTo(3);
        assertThat(tables(db, "'page_event_collection','page_events'")).isZero();
        assertThat(db.queryForList("SELECT script FROM flyway_schema_history WHERE version IN ('21','22','23') ORDER BY installed_rank", String.class))
            .containsExactly("V21__tap_recovery_evidence.sql", "V22__system_tap_recovery.sql", FUNNEL);
        assertThat(db.queryForList("SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='mission_funnel_events' AND index_name='ix_funnel_aggregate' ORDER BY seq_in_index", String.class))
            .containsExactly("questionnaire_version", "step", "user_id", "generation");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='mission_funnel_events' AND constraint_name='fk_funnel_user' AND referenced_table_name='users'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tap_recoveries' AND column_name='actor_id' AND is_nullable='YES'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM funnel_collection WHERE id=1 AND started_at IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    @Test void freshDatabaseMigratesV1ThroughV23AndEnforcesFunnelConstraints() {
        try (var mysql = mysql()) {
            var source = source(mysql); var db = new JdbcTemplate(source);
            migrate(source, "23"); structure(db);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success=1", Integer.class)).isEqualTo(23);
            db.update("INSERT INTO users(id,mattermost_id) VALUES (900,'isolated-migration-fresh')");
            String key = "11111111-1111-1111-1111-111111111111";
            event(db, 900, key, 0, 1);
            assertThatThrownBy(() -> event(db, 900, key, 0, 1)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> event(db, 901, "22222222-2222-2222-2222-222222222222", 0, 1)).isInstanceOf(DataIntegrityViolationException.class);
            rejectsCheck(() -> event(db, 900, "33333333-3333-3333-3333-333333333333", 0, 21), "ck_funnel_step");
            rejectsCheck(() -> event(db, 900, "44444444-4444-4444-4444-444444444444", -1, 1), "ck_funnel_generation");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_funnel_events", Integer.class)).isEqualTo(1);
        }
    }

    @Test void populatedV20UpgradesStepwiseAndPreservesRowsAndEveryExistingHistoryField() {
        try (var mysql = mysql()) {
            var source = source(mysql); var db = new JdbcTemplate(source);
            migrate(source, "20");
            db.update("INSERT INTO users(id,mattermost_id) VALUES (900,'isolated-migration-upgrade')");
            db.update("INSERT INTO tap_attempts(id,user_id,generation,idempotency_key,starts_at,ends_at,submit_deadline,status,tap_count) VALUES (900,900,1,'isolated-seed','2000-01-01 00:00:00','2000-01-01 00:00:10','2000-01-01 00:00:30','INVALID',3)");
            var user = db.queryForMap("SELECT * FROM users WHERE id=900");
            var attempt = db.queryForMap("SELECT * FROM tap_attempts WHERE id=900");
            var oldHistory = history(db);
            assertThat(tables(db, "'tap_recoveries','funnel_collection','mission_funnel_events'")).isZero();
            migrate(source, "21");
            assertThat(db.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tap_recoveries' AND column_name='actor_id'", String.class)).isEqualTo("NO");
            db.update("INSERT INTO tap_recoveries(attempt_id,user_id,actor_id,request_key,expected_generation,reason,original_status,original_deadline,effective_deadline,recognized_at) VALUES (900,900,900,'isolated-recovery',0,'isolated migration evidence','INVALID','2000-01-01 00:00:30','2000-01-01 00:00:30','2000-01-01 00:00:10')");
            var recovery = db.queryForMap("SELECT * FROM tap_recoveries WHERE attempt_id=900");
            var v21History = history(db);
            migrate(source, "22");
            assertThat(db.queryForMap("SELECT * FROM tap_recoveries WHERE attempt_id=900")).isEqualTo(recovery);
            var v22History = history(db);
            migrate(source, "23"); structure(db);
            assertThat(db.queryForMap("SELECT * FROM users WHERE id=900")).isEqualTo(user);
            assertThat(db.queryForMap("SELECT * FROM tap_attempts WHERE id=900")).isEqualTo(attempt);
            assertThat(db.queryForMap("SELECT * FROM tap_recoveries WHERE attempt_id=900")).isEqualTo(recovery);
            var finalHistory = history(db);
            for (var previous : List.of(oldHistory, v21History, v22History)) {
                assertThat(finalHistory.subList(0, previous.size())).isEqualTo(previous);
            }
            event(db, 900, "55555555-5555-5555-5555-555555555555", 0, 1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_funnel_events", Integer.class)).isEqualTo(1);
        }
    }

    private Path copiedMigrations(boolean legacyOnly) throws Exception {
        var resources = Path.of(getClass().getClassLoader().getResource("db/migration").toURI());
        var destination = Files.createDirectory(temporary.resolve(legacyOnly ? "legacy" : "duplicate"));
        try (var files = Files.list(resources)) {
            for (var file : files.toList()) {
                String name = file.getFileName().toString();
                boolean followup = name.startsWith("V21__") || name.startsWith("V22__") || name.startsWith("V23__");
                if (!legacyOnly || !followup) Files.copy(file, destination.resolve(name));
            }
        }
        Files.copy(resources.resolve(FUNNEL), destination.resolve("V21__add_mission_funnel_events.sql"));
        return destination;
    }

    @Test void retainedLegacyFunnelV21IsRejectedWithoutRepairOrHistoryMutation() throws Exception {
        try (var mysql = mysql()) {
            var source = source(mysql); var db = new JdbcTemplate(source);
            var legacy = flyway(source, "21", "filesystem:" + copiedMigrations(true));
            legacy.migrate(); legacy.validate();
            db.update("INSERT INTO users(id,mattermost_id) VALUES (900,'isolated-legacy-history')");
            event(db, 900, "66666666-6666-6666-6666-666666666666", 0, 1);
            var before = history(db);
            var candidate = flyway(source, "23", MIGRATIONS);
            assertThat(candidate.validateWithResult().validationSuccessful).isFalse();
            assertThatThrownBy(candidate::migrate).isInstanceOf(FlywayException.class);
            assertThat(history(db)).isEqualTo(before);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM mission_funnel_events", Integer.class)).isEqualTo(1);
            assertThat(tables(db, "'tap_recoveries'")).isZero();
        }
    }

    @Test void duplicateV21FilesAreRejectedBeforeAnyBusinessSchemaIsCreated() throws Exception {
        try (var mysql = mysql()) {
            var source = source(mysql); var db = new JdbcTemplate(source);
            var duplicate = flyway(source, null, "filesystem:" + copiedMigrations(false));
            assertThatThrownBy(duplicate::migrate).isInstanceOf(FlywayException.class).hasMessageContaining("version 21");
            assertThat(tables(db, "'users','tap_recoveries','funnel_collection','mission_funnel_events'")).isZero();
        }
    }
}
