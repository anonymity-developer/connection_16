package com.tagme.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration @EnableScheduling
public class ModerationConfiguration {
    @Bean @ConditionalOnMissingBean(ModerationProvider.class)
    ModerationProvider configuredModerationProvider(ObjectMapper json,
        @Value("${tagme.moderation.provider:NONE}") String provider,
        @Value("${tagme.moderation.gms-base-url:}") String gmsUrl,
        @Value("${tagme.moderation.gms-key:}") String gmsKey,
        @Value("${tagme.moderation.openai-key:}") String openaiKey) {
        if ("GMS".equals(provider) || "OPENAI".equals(provider)) {
            String url="GMS".equals(provider)?gmsUrl:"https://api.openai.com/v1";
            // A misconfigured endpoint must not send credentials or user text over plain HTTP.
            URI uri;
            try { uri=URI.create(url); } catch (IllegalArgumentException invalid) { uri=null; }
            if(uri==null||!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
                return text -> new ModerationProvider.Decision("REVIEW_REQUIRED","PROVIDER_NOT_CONFIGURED","SYSTEM","tagme-text-v1");
            var transport=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
            var factory=new JdkClientHttpRequestFactory(transport);factory.setReadTimeout(Duration.ofSeconds(12));
            var client=RestClient.builder().baseUrl(url.replaceAll("/+$", "")).requestFactory(factory).build();
            return new HttpModerationProvider(client,json,provider,"GMS".equals(provider)?"gpt-5-nano":"omni-moderation-latest","GMS".equals(provider)?gmsKey:openaiKey);
        }
        return text -> new ModerationProvider.Decision("REVIEW_REQUIRED","PROVIDER_NOT_CONFIGURED","NONE","unconfigured");
    }
}
