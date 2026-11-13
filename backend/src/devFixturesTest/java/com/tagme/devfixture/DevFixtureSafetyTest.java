package com.tagme.devfixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevFixtureSafetyTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final DevFixtureSafety safety = new DevFixtureSafety(environment, db);

    @BeforeEach void localConfiguration() {
        environment.setActiveProfiles("docker", "dev-fixtures");
        environment.setProperty("tagme.fixtures.project", "tagme-fixtures");
        when(db.queryForObject("SELECT DATABASE()", String.class)).thenReturn("tagme_fixtures");
        when(db.queryForObject(DevFixtureSafety.FOREIGN_PARTICIPANTS, Long.class)).thenReturn(0L);
    }

    @Test void allowsAnIsolatedFixtureDatabase() {
        assertThatCode(safety::verify).doesNotThrowAnyException();
    }

    @Test void requiresTheExplicitProjectBeforeAccessingTheDatabase() {
        environment.setProperty("tagme.fixtures.project", "tagme-prod");
        assertThatThrownBy(safety::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("project");
        verifyNoInteractions(db);
    }

    @Test void rejectsMissingProject() {
        environment.getPropertySources().remove("mockProperties");
        assertThatThrownBy(safety::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("project");
        verifyNoInteractions(db);
    }

    @Test void rejectsProductionCookiesBeforeAccessingTheDatabase() {
        environment.setProperty("server.servlet.session.cookie.secure", "true");
        assertThatThrownBy(safety::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("production");
        verifyNoInteractions(db);
    }

    @Test void rejectsProductionEnvironmentOverride() {
        environment.setProperty("APP_SESSION_SECURE", "true");
        assertThatThrownBy(safety::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("production");
        verifyNoInteractions(db);
    }

    @Test void rejectsBothProductionProfileNames() {
        for (String profile : new String[]{"prod", "production"}) {
            environment.setActiveProfiles("docker", "dev-fixtures", profile);
            assertThatThrownBy(safety::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("production");
        }
        verifyNoInteractions(db);
    }

    @Test void checksTheActualConnectionDatabase() {
        when(db.queryForObject("SELECT DATABASE()", String.class)).thenReturn("tagme");
        assertThatThrownBy(safety::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("database");
        verify(db, never()).queryForObject(DevFixtureSafety.FOREIGN_PARTICIPANTS, Long.class);
    }

    @Test void refusesToMixRealParticipantsIntoFixtures() {
        when(db.queryForObject(DevFixtureSafety.FOREIGN_PARTICIPANTS, Long.class)).thenReturn(1L);
        assertThatThrownBy(safety::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("non-fixture");
    }

    @Test void refusesAnUnknownParticipantCount() {
        when(db.queryForObject(DevFixtureSafety.FOREIGN_PARTICIPANTS, Long.class)).thenReturn(null);
        assertThatThrownBy(safety::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("non-fixture");
    }
}
