package com.tagme.auth;

import java.net.URI;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MattermostClientTest {
    private static final String USER = "a".repeat(26), CHANNEL = "c".repeat(26), BOT = "b".repeat(26);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AuthProperties properties = new AuthProperties(true, URI.create("http://localhost"), "test-only-token", CHANNEL,
            Base64.getEncoder().encodeToString(new byte[32]), 180, 60, 5, 5, 10, 120);
    private final MattermostClient client = new MattermostClient(properties, builder);
    @Test void lookupValidatesImmutableIdAndMembershipThenSendsOnlyOtp() {
        server.expect(requestTo("http://localhost/api/v4/users/username/alice")).andExpect(header("Authorization", "Bearer test-only-token"))
                .andRespond(withSuccess("{\"id\":\"" + USER + "\",\"username\":\"alice\",\"delete_at\":0,\"unexpected\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/api/v4/channels/" + CHANNEL + "/members/" + USER))
                .andRespond(withSuccess("{\"user_id\":\"" + USER + "\",\"channel_id\":\"" + CHANNEL + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/api/v4/users/me")).andRespond(withSuccess("{\"id\":\"" + BOT + "\",\"delete_at\":0,\"is_bot\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/api/v4/channels/direct")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[\"" + BOT + "\",\"" + USER + "\"]"))
                .andRespond(withSuccess("{\"id\":\"direct-channel\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/api/v4/posts")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.channel_id").value("direct-channel"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("012345")))
                .andRespond(withSuccess("{\"id\":\"post-id\"}", MediaType.APPLICATION_JSON));
        var identity = client.byUsername("alice").orElseThrow();
        client.sendCode(identity, OtpStore.Purpose.SIGN_UP, "012345"); server.verify();
    }
    @Test void nonmemberAndUpstreamDenialNeverBecomeAuthorized() {
        server.expect(requestTo("http://localhost/api/v4/users/username/alice"))
                .andRespond(withSuccess("{\"id\":\"" + USER + "\",\"username\":\"alice\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/api/v4/channels/" + CHANNEL + "/members/" + USER)).andRespond(withResourceNotFound());
        assertThat(client.byUsername("alice")).isEmpty(); server.verify();
    }
    @Test void tokenOrPermissionFailureIsUnavailableRatherThanMissingMember() {
        server.expect(requestTo("http://localhost/api/v4/users/username/alice")).andRespond(withUnauthorizedRequest());
        assertThatThrownBy(() -> client.byUsername("alice")).isInstanceOf(AuthException.class).satisfies(error -> assertThat(((AuthException)error).status()).isEqualTo(503));
    }
}
