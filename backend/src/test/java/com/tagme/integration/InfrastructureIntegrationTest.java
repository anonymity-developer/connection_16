package com.tagme.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InfrastructureIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TestRestTemplate http;

    @Test
    void realDependenciesAreReadyAndMigrationsSeedFiveUniqueMissions() {
        var response = http.getForEntity("/actuator/health/readiness", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM missions", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT GROUP_CONCAT(letter ORDER BY sort_order SEPARATOR '') FROM missions", String.class))
                .isEqualTo("SSAFY");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO missions (code, letter, title, hint, sort_order) VALUES ('SHOW_ME', 'S', 'duplicate', '', 6)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
