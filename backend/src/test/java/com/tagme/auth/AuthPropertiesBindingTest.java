package com.tagme.auth;

import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;

class AuthPropertiesBindingTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    static class Config { }
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class)
            .withPropertyValues("tagme.auth.enabled=true", "tagme.auth.base-url=http://localhost",
                "tagme.auth.hmac-key=" + Base64.getEncoder().encodeToString(new byte[32]));
    @Test void bindsSenderSessionCredentialsAndKeepsDiagnosticOutputRedacted() {
        runner.withPropertyValues("tagme.auth.login-id=test-sender", "tagme.auth.login-password=not-a-real-secret",
            "tagme.auth.team-name=test-team", "tagme.auth.channel-name=seoul-test")
            .run(context -> {
                assertThat(context).hasNotFailed();
                var config = context.getBean(AuthProperties.class);
                assertThat(config.loginId()).isEqualTo("test-sender");
                assertThat(config.loginPassword()).isEqualTo("not-a-real-secret");
                assertThat(config.teamName()).isEqualTo("test-team");
                assertThat(config.toString()).isEqualTo("AuthProperties[redacted]");
            });
    }
    @Test void bindsExistingPatConfigurationWithoutRequiringSenderPassword() {
        runner.withPropertyValues("tagme.auth.token=test-pat", "tagme.auth.channel-id=" + "c".repeat(26))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(AuthProperties.class).loginPassword()).isEmpty();
            });
    }
    @Test void defaultDisabledModeStillStartsWithoutCredentials() {
        new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
            assertThat(context).hasNotFailed(); assertThat(context.getBean(AuthProperties.class).enabled()).isFalse();
        });
    }
    static Stream<org.junit.jupiter.params.provider.Arguments> invalidConfigs() {
        String channel = "tagme.auth.channel-id=" + "c".repeat(26);
        return Stream.of(
            new String[] {channel},
            new String[] {channel, "tagme.auth.token=test-pat", "tagme.auth.login-id=partial"},
            new String[] {channel, "tagme.auth.token=test-pat", "tagme.auth.login-password=partial"},
            new String[] {channel, "tagme.auth.token=test-pat", "tagme.auth.login-id=test", "tagme.auth.login-password=test"},
            new String[] {channel, "tagme.auth.token=test-pat", "tagme.auth.team-name=partial"},
            new String[] {"tagme.auth.token=test-pat", "tagme.auth.channel-id=bad-id", "tagme.auth.team-name=test-team", "tagme.auth.channel-name=seoul-test"},
            new String[] {channel, "tagme.auth.token=test-pat", "tagme.auth.team-name=test-team", "tagme.auth.channel-name=seoul-test"},
            new String[] {"tagme.auth.token=test-pat", "tagme.auth.team-name=../invalid", "tagme.auth.channel-name=seoul-test"}
        ).map(properties -> org.junit.jupiter.params.provider.Arguments.of((Object) properties));
    }
    @ParameterizedTest @MethodSource("invalidConfigs")
    void rejectsPartialOrAmbiguousConfiguration(String[] properties) {
        runner.withPropertyValues(properties).run(context -> assertThat(context).hasFailed());
    }
}
