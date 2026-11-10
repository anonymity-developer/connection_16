package com.tagme.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MattermostClient {
    public record Identity(String id, String username, MattermostProfile profile) {
        @Override public String toString() { return "Identity[redacted]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    record User(String id, String username, @JsonProperty("delete_at") Long deleteAt, @JsonProperty("is_bot") boolean bot, @JsonProperty("first_name") String firstName, @JsonProperty("last_name") String lastName, String nickname, @JsonProperty("last_picture_update") Long pictureUpdatedAt) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Member(@JsonProperty("user_id") String userId, @JsonProperty("channel_id") String channelId) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Channel(String id) { }
    private final AuthProperties properties;
    private final RestClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public MattermostClient(AuthProperties properties) {
        this(properties, builder());
    }
    private static RestClient.Builder builder() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client); factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(factory);
    }
    MattermostClient(AuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        http = builder.baseUrl(properties.baseUrl().toString().replaceAll("/+$", "") + "/api/v4")
                .build();
    }
    // PAT remains supported. The session flow is used only when PAT is absent.
    private <T> T withSender(java.util.function.Function<String, T> operation) {
        if (!properties.enabled()) throw AuthException.unavailable();
        if (!properties.token().isBlank()) return operation.apply(properties.token());

        final org.springframework.http.ResponseEntity<String> login;
        try {
            login = http.post().uri("/users/login")
                    .body(Map.of("login_id", properties.loginId(), "password", properties.loginPassword()))
                    .retrieve().toEntity(String.class);
        } catch (RestClientException exception) { throw AuthException.unavailable(); }
        var issuedToken = login.getHeaders().getFirst("Token");
        if (!login.getStatusCode().is2xxSuccessful() || issuedToken == null
                || !issuedToken.matches("[A-Za-z0-9_-]{1,512}")) throw AuthException.unavailable();
        // Keep the token request-local; even an invalid login body must reach cleanup.
        try {
            final User sender;
            try { sender = new com.fasterxml.jackson.databind.ObjectMapper().readValue(login.getBody(), User.class); }
            catch (java.io.IOException | IllegalArgumentException exception) { throw AuthException.unavailable(); }
            if (sender == null || sender.id() == null || !sender.id().matches("[a-z0-9]{26}")
                    || sender.deleteAt() == null || sender.deleteAt() != 0) throw AuthException.unavailable();
            return operation.apply(issuedToken);
        } finally {
            try {
                http.post().uri("/users/logout").header("Authorization", "Bearer " + issuedToken)
                        .retrieve().toBodilessEntity();
            } catch (RestClientException ignored) {
                // Do not retry a possibly sent DM or mask the original result/error.
                // Server-side revocation cannot be guaranteed if logout is unavailable.
            }
        }
    }
    public Optional<Identity> byUsername(String username) {
        return withSender(token -> eligible(token, get(token, User.class, "users", "username", username)));
    }
    public Optional<Identity> byId(String id) {
        return withSender(token -> eligible(token, get(token, User.class, "users", id)));
    }
    private String resolveEligibleChannelId(String token) {
        if (!properties.channelId().isBlank()) return properties.channelId();
        var team = get(token, Channel.class, "teams", "name", properties.teamName());
        if (team == null || team.id() == null || !team.id().matches("[a-z0-9]{26}"))
            throw AuthException.unavailable();
        var channel = get(token, Channel.class, "teams", team.id(), "channels", "name", properties.channelName());
        if (channel == null || channel.id() == null || !channel.id().matches("[a-z0-9]{26}"))
            throw AuthException.unavailable();
        return channel.id();
    }
    private Optional<Identity> eligible(String token, User user) {
        if (user == null) return Optional.empty();
        if (user.deleteAt() == null) throw AuthException.unavailable();
        if (user.deleteAt() != 0 || user.bot()) return Optional.empty();
        if (user.id() == null || !user.id().matches("[a-z0-9]{26}") || user.username() == null
                || !user.username().matches("[a-z0-9._-]{1,64}")) throw AuthException.unavailable();
        var channelId = resolveEligibleChannelId(token);
        var member = get(token, Member.class, "channels", channelId, "members", user.id());
        if (member == null) return Optional.empty();
        if (!user.id().equals(member.userId()) || !channelId.equals(member.channelId())) throw AuthException.unavailable();
        return Optional.of(new Identity(user.id(), user.username(),MattermostProfile.parse(user.firstName(),user.lastName(),user.nickname(),user.pictureUpdatedAt()==null?0:user.pictureUpdatedAt())));
    }
    private <T> T get(String token, Class<T> type, String... segments) {
        try { return http.get().uri(builder -> builder.pathSegment(segments).build())
                .header("Authorization", "Bearer " + token).retrieve().body(type); }
        catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return null;
            throw AuthException.unavailable();
        } catch (RestClientException exception) { throw AuthException.unavailable(); }
    }
    public byte[] photo(String externalId) {
        if(externalId==null||!externalId.matches("[a-z0-9]{26}"))throw AuthException.unavailable();
        return withSender(token -> {
        try { return http.get().uri(b->b.pathSegment("users",externalId,"image").build())
                .header("Authorization", "Bearer " + token).exchange((request,response)->{
            if(response.getStatusCode().value()==404)return null;
            if(!response.getStatusCode().is2xxSuccessful()||!org.springframework.http.MediaType.IMAGE_PNG.isCompatibleWith(response.getHeaders().getContentType()))throw AuthException.unavailable();
            byte[] bytes=response.getBody().readNBytes(1024*1024+1);
            byte[] signature={(byte)137,80,78,71,13,10,26,10};
            if(bytes.length>1024*1024||bytes.length<8||!java.util.Arrays.equals(signature,java.util.Arrays.copyOf(bytes,8)))throw AuthException.unavailable();
            return bytes;
        }); } catch(RestClientException failure){throw AuthException.unavailable();}
        });
    }
    public void sendCode(Identity target, OtpStore.Purpose purpose, String code) {
        withSender(token -> {
        try {
            var sender = get(token, User.class, "users", "me");
            if (sender == null || sender.id() == null || sender.deleteAt() == null || sender.deleteAt() != 0 || target.id().equals(sender.id())) throw AuthException.unavailable();
            var channel = http.post().uri("/channels/direct").header("Authorization", "Bearer " + token)
                    .body(List.of(sender.id(), target.id())).retrieve().body(Channel.class);
            if (channel == null || channel.id() == null || channel.id().isBlank()) throw AuthException.unavailable();
            var message = OtpMessage.format(purpose.label(), code, properties.otpTtlSeconds());
            var post = http.post().uri("/posts").header("Authorization", "Bearer " + token)
                    .body(Map.of("channel_id", channel.id(), "message", message)).retrieve().body(Channel.class);
            if (post == null || post.id() == null || post.id().isBlank()) throw AuthException.unavailable();
        } catch (RestClientException exception) { throw AuthException.unavailable(); }
        return null;
        });
    }
}
