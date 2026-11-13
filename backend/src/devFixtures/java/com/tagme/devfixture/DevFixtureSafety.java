package com.tagme.devfixture;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reject mixed/local-production configurations before application runners can seed anything. */
@Component
@Profile("dev-fixtures")
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class DevFixtureSafety implements ApplicationRunner {
    static final String FOREIGN_PARTICIPANTS = "SELECT COUNT(*) FROM users WHERE status='ACTIVE' AND role<>'ADMIN'"
            + " AND NOT COALESCE(mattermost_id REGEXP '^devfixture[0-9]{16}$',FALSE)";
    private final Environment environment;
    private final JdbcTemplate db;

    public DevFixtureSafety(Environment environment, JdbcTemplate db) {
        this.environment = environment;
        this.db = db;
    }

    @Override
    public void run(ApplicationArguments args) {
        verify();
    }

    void verify() {
        if (!"tagme-fixtures".equals(environment.getProperty("tagme.fixtures.project")))
            throw new IllegalStateException("Fixtures require the isolated tagme-fixtures Compose project");
        if (environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)
                || environment.getProperty("APP_SESSION_SECURE", Boolean.class, false)
                || Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equals("prod") || p.equals("production")))
            throw new IllegalStateException("Fixtures cannot run with production session/profile settings");
        if (!"tagme_fixtures".equals(db.queryForObject("SELECT DATABASE()", String.class)))
            throw new IllegalStateException("Fixtures require the dedicated tagme_fixtures database");
        Long foreign = db.queryForObject(FOREIGN_PARTICIPANTS, Long.class);
        if (foreign == null || foreign != 0)
            throw new IllegalStateException("Refusing to seed a database containing non-fixture participants");
    }
}
