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
    public record Identity(String id, String username) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    record User(String id, String username, @JsonProperty("delete_at") long deleteAt, @JsonProperty("is_bot") boolean bot) { }
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
                .defaultHeader("Authorization", "Bearer " + properties.token()).build();
    }
    public Optional<Identity> byUsername(String username) { return eligible(get(User.class, "users", "username", username)); }
    public Optional<Identity> byId(String id) { return eligible(get(User.class, "users", id)); }
    private Optional<Identity> eligible(User user) {
        if (user == null || user.deleteAt() != 0 || user.bot()) return Optional.empty();
        if (user.id() == null || !user.id().matches("[a-z0-9]{26}") || user.username() == null
                || !user.username().matches("[a-z0-9._-]{1,64}")) throw AuthException.unavailable();
        var member = get(Member.class, "channels", properties.channelId(), "members", user.id());
        if (member == null) return Optional.empty();
        if (!user.id().equals(member.userId()) || !properties.channelId().equals(member.channelId())) throw AuthException.unavailable();
        return Optional.of(new Identity(user.id(), user.username()));
    }
    private <T> T get(Class<T> type, String... segments) {
        try { return http.get().uri(builder -> builder.pathSegment(segments).build()).retrieve().body(type); }
        catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return null;
            throw AuthException.unavailable();
        } catch (RestClientException exception) { throw AuthException.unavailable(); }
    }
    public void sendCode(Identity target, OtpStore.Purpose purpose, String code) {
        try {
            var sender = get(User.class, "users", "me");
            if (sender == null || sender.id() == null || sender.deleteAt() != 0 || target.id().equals(sender.id())) throw AuthException.unavailable();
            var channel = http.post().uri("/channels/direct").body(List.of(sender.id(), target.id())).retrieve().body(Channel.class);
            if (channel == null || channel.id() == null || channel.id().isBlank()) throw AuthException.unavailable();
            var message = "TAG ME " + purpose.label() + " 인증번호: " + code + "\n"
                    + properties.otpTtlSeconds() + "초 이내에 입력해 주세요. 타인에게 공유하지 마세요. 요청한 적이 없다면 무시해 주세요.";
            var post = http.post().uri("/posts").body(Map.of("channel_id", channel.id(), "message", message)).retrieve().body(Channel.class);
            if (post == null || post.id() == null || post.id().isBlank()) throw AuthException.unavailable();
        } catch (RestClientException exception) { throw AuthException.unavailable(); }
    }
}
