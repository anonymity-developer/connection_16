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
                .andRespond(withSuccess("{\"id\":\"" + USER + "\",\"username\":\"alice\",\"delete_at\":0}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/api/v4/channels/" + CHANNEL + "/members/" + USER)).andRespond(withResourceNotFound());
        assertThat(client.byUsername("alice")).isEmpty(); server.verify();
    }
    @Test void missingAccountStateFailsClosed() {
        server.expect(requestTo("http://localhost/api/v4/users/username/alice"))
                .andRespond(withSuccess("{\"id\":\"" + USER + "\",\"username\":\"alice\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.byUsername("alice")).isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException)error).status()).isEqualTo(503));
    }
    @Test void tokenOrPermissionFailureIsUnavailableRatherThanMissingMember() {
        server.expect(requestTo("http://localhost/api/v4/users/username/alice")).andRespond(withUnauthorizedRequest());
        assertThatThrownBy(() -> client.byUsername("alice")).isInstanceOf(AuthException.class).satisfies(error -> assertThat(((AuthException)error).status()).isEqualTo(503));
    }
    @Test void importsStructuredNameAndPhotoVersionOnlyAfterChannelMembership() {
        server.expect(requestTo("http://localhost/api/v4/users/username/alice")).andRespond(withSuccess("{\"id\":\""+USER+"\",\"username\":\"alice\",\"delete_at\":0,\"first_name\":\"테스트[구미_3반]\",\"last_name\":\"\",\"last_picture_update\":123}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/api/v4/channels/"+CHANNEL+"/members/"+USER)).andRespond(withSuccess("{\"user_id\":\""+USER+"\",\"channel_id\":\""+CHANNEL+"\"}",MediaType.APPLICATION_JSON));
        assertThat(client.byUsername("alice").orElseThrow().profile()).isEqualTo(new MattermostProfile("테스트","구미",3,123));server.verify();
    }
    @Test void photoUsesServerTokenAndRejectsHtmlSvgAndOversizedBodies() {
        byte[] png={(byte)137,80,78,71,13,10,26,10,0};
        server.expect(requestTo("http://localhost/api/v4/users/"+USER+"/image")).andExpect(header("Authorization","Bearer test-only-token")).andRespond(withSuccess(png,MediaType.IMAGE_PNG));
        server.expect(requestTo("http://localhost/api/v4/users/"+USER+"/image")).andRespond(withSuccess("<svg/>",MediaType.valueOf("image/svg+xml")));
        server.expect(requestTo("http://localhost/api/v4/users/"+USER+"/image")).andRespond(withSuccess(new byte[1024*1024+1],MediaType.IMAGE_PNG));
        assertThat(client.photo(USER)).isEqualTo(png);assertThatThrownBy(()->client.photo(USER)).isInstanceOf(AuthException.class);assertThatThrownBy(()->client.photo(USER)).isInstanceOf(AuthException.class);server.verify();
    }
}
