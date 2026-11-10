package com.tagme.auth;

import com.tagme.common.config.SecurityConfig;
import com.tagme.participation.*;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.session.*;
import org.springframework.session.web.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static com.tagme.participation.ParticipationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Servlet/security contract with an in-memory Spring Session test double.
// This does not replace the real Redis/MySQL Testcontainers suite.
@WebMvcTest({AuthController.class, ParticipationController.class})
@Import({SecurityConfig.class, AuthSessions.class, AuthSessionLifecycleTest.SessionConfig.class})
class AuthSessionLifecycleTest {
    @TestConfiguration
    static class SessionConfig {
        @Bean MapSessionRepository testSessionRepository(@Value("${spring.session.timeout}") String timeout) {
            var repository = new MapSessionRepository(new ConcurrentHashMap<>());
            repository.setDefaultMaxInactiveInterval(DurationStyle.detectAndParse(timeout));
            return repository;
        }
        @Bean @Order(SessionRepositoryFilter.DEFAULT_ORDER)
        SessionRepositoryFilter<MapSession> testSessionFilter(MapSessionRepository repository) {
            var filter = new SessionRepositoryFilter<>(repository);
            var serializer = new DefaultCookieSerializer();
            serializer.setCookieName("TAGME_SESSION");
            serializer.setUseBase64Encoding(false); // Test lookup only; production serializer stays unchanged.
            var resolver = new CookieHttpSessionIdResolver(); resolver.setCookieSerializer(serializer);
            filter.setHttpSessionIdResolver(resolver);
            return filter;
        }
    }
    @Autowired MockMvc mvc;
    @Autowired MapSessionRepository repository;
    @MockitoBean AuthService service;
    @MockitoBean AuthRepository accounts;
    @MockitoBean AuthProperties properties;
    @MockitoBean ParticipationService participation;
    @MockitoBean FindByIndexNameSessionRepository<?> indexedSessions;
    final AuthRepository.Account account = new AuthRepository.Account(1, "a".repeat(26), "alice", "ACTIVE", "USER", "unused", 1L, "Alice", false);
    @BeforeEach void setup() {
        when(service.login(anyString(), anyString(), anyString())).thenReturn(account);
        when(service.signup(anyString(), anyString(), anyString(), anyString())).thenReturn(account);
        when(accounts.byId(1L)).thenReturn(Optional.of(account));
        when(participation.pendingHash(anyString(), anyString())).thenReturn("test-hash-only");
    }
    Cookie cookie(MvcResult result) { return result.getResponse().getCookie("TAGME_SESSION"); }
    Cookie login() throws Exception {
        return cookie(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
            .content("{\"username\":\"alice\",\"password\":\"a long test password\"}"))
            .andExpect(status().isOk()).andReturn());
    }
    @Test void fourHourIdleExpiryRefreshAndLogoutAreServerEnforced() throws Exception {
        Cookie first = login(), second = login();
        var session = repository.findById(first.getValue());
        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofHours(4));
        // 3h59m idle is valid; a real request refreshes last access.
        session.setLastAccessedTime(Instant.now().minus(Duration.ofHours(4)).plusSeconds(60)); repository.save(session);
        mvc.perform(get("/api/v1/users/me").cookie(first)).andExpect(status().isOk());
        assertThat(repository.findById(first.getValue()).getLastAccessedTime()).isAfter(Instant.now().minusSeconds(10));
        session = repository.findById(first.getValue());
        session.setLastAccessedTime(Instant.now().minus(Duration.ofHours(4))); repository.save(session);
        mvc.perform(get("/api/v1/users/me").cookie(first)).andExpect(status().isUnauthorized());
        assertThat(repository.findById(first.getValue())).isNull();
        mvc.perform(get("/api/v1/users/me").cookie(second)).andExpect(status().isOk());
        Cookie fresh = login();
        mvc.perform(post("/api/v1/auth/logout").cookie(fresh).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me").cookie(fresh)).andExpect(status().isUnauthorized());
        assertThat(repository.findById(fresh.getValue())).isNull();
        mvc.perform(get("/api/v1/users/me").cookie(second)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
    @Test void passwordVersionChangeRejectsBothDevicesEvenIfRedisCleanupWasMissed() throws Exception {
        Cookie first = login(), second = login();
        when(accounts.byId(1L)).thenReturn(Optional.of(new AuthRepository.Account(1, "a".repeat(26),
            "alice", "ACTIVE", "USER", "unused", 2L, "Alice", false)));
        for (Cookie old : new Cookie[] { first, second }) {
            mvc.perform(get("/api/v1/users/me").cookie(old)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
            assertThat(repository.findById(old.getValue())).isNull();
        }
    }
    static Stream<Arguments> tagEntryCases() {
        return Stream.of(MissionCode.values()).flatMap(code -> Stream.of("login", "signup").map(mode -> Arguments.of(code, mode)));
    }
    @ParameterizedTest @MethodSource("tagEntryCases")
    void everyMissionSurvivesSessionRotationAndResumes(MissionCode code, String mode) throws Exception {
        String token = "A".repeat(43), route = "/missions/" + code.name().toLowerCase().replace('_', '-');
        when(participation.resolve(account.principal(), "test-hash-only"))
            .thenReturn(new TagResult(code, "UNLOCKED", route, true, false));
        Cookie anonymous = cookie(mvc.perform(post("/api/v1/tags/pending").with(csrf()).contentType("application/json")
            .content("{\"token\":\"" + token + "\"}")).andExpect(status().isAccepted()).andReturn());
        mvc.perform(get("/api/v1/tags/pending").cookie(anonymous)).andExpect(jsonPath("$.present").value(true));
        verify(participation, never()).resolve(any(), anyString());
        String body = mode.equals("login") ? "{\"username\":\"alice\",\"password\":\"a long test password\"}"
            : "{\"challengeId\":\"" + "B".repeat(43) + "\",\"code\":\"012345\",\"password\":\"a long test password\"}";
        Cookie authenticated = cookie(mvc.perform(post("/api/v1/auth/" + mode).cookie(anonymous).with(csrf())
            .contentType("application/json").content(body)).andExpect(status().is(mode.equals("signup") ? 201 : 200)).andReturn());
        assertThat(authenticated.getValue()).isNotEqualTo(anonymous.getValue());
        assertThat(repository.findById(anonymous.getValue())).isNull();
        mvc.perform(get("/api/v1/tags/pending").cookie(authenticated)).andExpect(jsonPath("$.present").value(true));
        mvc.perform(post("/api/v1/tags/resume").cookie(authenticated).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.missionCode").value(code.name())).andExpect(jsonPath("$.route").value(route));
        mvc.perform(get("/api/v1/tags/pending").cookie(authenticated)).andExpect(jsonPath("$.present").value(false));
        mvc.perform(post("/api/v1/tags/resume").cookie(authenticated).with(csrf())).andExpect(status().isNotFound());
        verify(participation, times(1)).resolve(account.principal(), "test-hash-only");
    }
}
