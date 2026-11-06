package com.tagme.auth;

import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MattermostSenderSessionTest {
    private static final String USER = "a".repeat(26), CHANNEL = "c".repeat(26), SENDER = "b".repeat(26), TEAM = "d".repeat(26);
    private static final String TOKEN = "test-session-only", PASSWORD = "test-only-password";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private AuthProperties properties(String channelId, String team, String channel) {
        return new AuthProperties(true, URI.create("http://localhost"), "", "test-operator", PASSWORD,
                channelId, team, channel, Base64.getEncoder().encodeToString(new byte[32]), 180, 60, 5, 5, 10, 120);
    }
    private MattermostClient client() { return new MattermostClient(properties(CHANNEL, "", ""), builder); }
    private String json(Object body) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new AssertionError(exception); }
    }
    private Map<String, Object> user(String id) { return Map.of("id", id, "username", "alice", "delete_at", 0); }
    private void login(String token) {
        server.expect(requestTo("http://localhost/api/v4/users/login")).andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().json(json(Map.of("login_id", "test-operator", "password", PASSWORD))))
                .andRespond(withSuccess(json(user(SENDER)), MediaType.APPLICATION_JSON).header("Token", token));
    }
    private void logout(String token) {
        server.expect(requestTo("http://localhost/api/v4/users/logout")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + token)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }
    private void lookup(String token) {
        server.expect(requestTo("http://localhost/api/v4/users/username/alice"))
                .andExpect(header("Authorization", "Bearer " + token)).andRespond(withSuccess(json(user(USER)), MediaType.APPLICATION_JSON));
    }
    private void membership() {
        server.expect(requestTo("http://localhost/api/v4/channels/" + CHANNEL + "/members/" + USER))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(json(Map.of("user_id", USER, "channel_id", CHANNEL)), MediaType.APPLICATION_JSON));
    }
    private void unavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).status()).isEqualTo(503))
                .hasMessageNotContaining(PASSWORD).hasMessageNotContaining(TOKEN);
        server.verify();
    }
    @Test void sessionLookupAndLogoutWithoutPat() {
        login(TOKEN); lookup(TOKEN); membership(); logout(TOKEN);
        assertThat(client().byUsername("alice")).isPresent(); server.verify();
    }
    @Test void namesResolveOnlyConfiguredChannelThenVerifyMembership() {
        login(TOKEN); lookup(TOKEN);
        server.expect(requestTo("http://localhost/api/v4/teams/name/test-team"))
                .andExpect(header("Authorization", "Bearer " + TOKEN)).andRespond(withSuccess(json(Map.of("id", TEAM)), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/api/v4/teams/" + TEAM + "/channels/name/seoul-test"))
                .andExpect(header("Authorization", "Bearer " + TOKEN)).andRespond(withSuccess(json(Map.of("id", CHANNEL)), MediaType.APPLICATION_JSON));
        membership(); logout(TOKEN);
        assertThat(new MattermostClient(properties("", "test-team", "seoul-test"), builder).byUsername("alice")).isPresent(); server.verify();
    }
    @ParameterizedTest @ValueSource(ints = {401, 403, 429, 500})
    void loginFailureNeverFallsBackToPatOrAuthorizes(int status) {
        server.expect(requestTo("http://localhost/api/v4/users/login")).andRespond(withStatus(HttpStatus.valueOf(status)).body(PASSWORD));
        unavailable(() -> client().byUsername("alice"));
    }
    @Test void loginTimeoutFailsWithoutRetry() {
        server.expect(requestTo("http://localhost/api/v4/users/login")).andRespond(withException(new IOException("timeout")));
        unavailable(() -> client().byUsername("alice"));
    }
    @Test void missingTokenNeverUsesCookieOrBodyAsCredential() {
        server.expect(requestTo("http://localhost/api/v4/users/login"))
                .andRespond(withSuccess(json(user(SENDER)), MediaType.APPLICATION_JSON).header("Set-Cookie", "MMAUTHTOKEN=" + TOKEN));
        unavailable(() -> client().byUsername("alice"));
    }
    @ParameterizedTest @ValueSource(strings = {"not-json", "{}", "null", "{\"id\":\"bad\",\"delete_at\":0}"})
    void invalidLoginBodyStillRevokesIssuedToken(String body) {
        server.expect(requestTo("http://localhost/api/v4/users/login"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).header("Token", TOKEN));
        logout(TOKEN); unavailable(() -> client().byUsername("alice"));
    }
    @Test void nonMemberIsDeniedAndTokenIsRevoked() {
        login(TOKEN); lookup(TOKEN);
        server.expect(requestTo("http://localhost/api/v4/channels/" + CHANNEL + "/members/" + USER)).andRespond(withResourceNotFound());
        logout(TOKEN); assertThat(client().byUsername("alice")).isEmpty(); server.verify();
    }
    @Test void mismatchedMembershipFailsClosedAndRevokes() {
        login(TOKEN); lookup(TOKEN);
        server.expect(requestTo("http://localhost/api/v4/channels/" + CHANNEL + "/members/" + USER))
                .andRespond(withSuccess(json(Map.of("user_id", SENDER, "channel_id", CHANNEL)), MediaType.APPLICATION_JSON));
        logout(TOKEN); unavailable(() -> client().byUsername("alice"));
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void dmUsesSingleTemporaryTokenAndAlwaysLogsOut(boolean accepted) {
        login(TOKEN);
        server.expect(requestTo("http://localhost/api/v4/users/me")).andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(json(user(SENDER)), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/api/v4/channels/direct")).andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json(json(List.of(SENDER, USER))))
                .andRespond(withSuccess(json(Map.of("id", "dm-channel")), MediaType.APPLICATION_JSON));
        var post = server.expect(requestTo("http://localhost/api/v4/posts")).andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("012345")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PASSWORD))));
        if (accepted) post.andRespond(withSuccess(json(Map.of("id", "post-id")), MediaType.APPLICATION_JSON));
        else post.andRespond(withServerError());
        logout(TOKEN);
        Runnable send = () -> client().sendCode(new MattermostClient.Identity(USER, "alice", null), OtpStore.Purpose.SIGN_UP, "012345");
        if (accepted) { send.run(); server.verify(); } else unavailable(send);
    }
    @Test void photoAlsoUsesTemporarySessionAndCleanupOnInvalidContent() {
        login(TOKEN);
        server.expect(requestTo("http://localhost/api/v4/users/" + USER + "/image")).andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess("<svg/>", MediaType.valueOf("image/svg+xml")));
        logout(TOKEN); unavailable(() -> client().photo(USER));
    }
    @Test void logoutFailureDoesNotRetryOrReplaceSuccessfulResult() {
        login(TOKEN); lookup(TOKEN); membership();
        server.expect(requestTo("http://localhost/api/v4/users/logout")).andRespond(withServerError());
        assertThat(client().byUsername("alice")).isPresent(); server.verify();
    }
    @Test void eachOperationGetsItsOwnToken() {
        var client = client();
        for (String token : List.of("first-token", "second-token")) {
            login(token);
            server.expect(requestTo("http://localhost/api/v4/users/" + USER)).andExpect(header("Authorization", "Bearer " + token))
                    .andRespond(withResourceNotFound());
            logout(token);
        }
        assertThat(client.byId(USER)).isEmpty(); assertThat(client.byId(USER)).isEmpty(); server.verify();
    }
}
