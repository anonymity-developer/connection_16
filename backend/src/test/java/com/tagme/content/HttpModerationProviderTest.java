package com.tagme.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpModerationProviderTest {
    final ObjectMapper json=new ObjectMapper();
    final RestClient.Builder builder=RestClient.builder().baseUrl("https://moderation.test/v1");
    final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
    HttpModerationProvider provider(String name) { return new HttpModerationProvider(builder.build(),json,name,"GMS".equals(name)?"gpt-5-nano":"omni-moderation-latest","test-only-key"); }
    String completion(String status,String reason,String finish)throws Exception {
        return json.writeValueAsString(Map.of("choices",new Object[]{Map.of("finish_reason",finish,"message",Map.of("content",json.writeValueAsString(Map.of("status",status,"reason",reason))))}));
    }
    @Test void gmsUsesChatEnvelopeAndAllowsOrdinaryAnxiety()throws Exception {
        var provider=provider("GMS");
        server.expect(requestTo("https://moderation.test/v1/chat/completions"))
            .andExpect(header("Authorization","Bearer test-only-key"))
            .andExpect(jsonPath("$.model").value("gpt-5-nano"))
            .andExpect(jsonPath("$.messages[1].content").value("시험이 걱정돼요"))
            .andExpect(jsonPath("$.temperature").doesNotExist())
            .andRespond(withSuccess(completion("APPROVED","SAFE","stop"),MediaType.APPLICATION_JSON));
        assertThat(provider.inspect("시험이 걱정돼요").status()).isEqualTo("APPROVED");server.verify();
    }
    @Test void explicitAbuseIsRejectedButPersonalDataRequiresReview()throws Exception {
        var provider=provider("GMS");
        server.expect(requestTo("https://moderation.test/v1/chat/completions")).andRespond(withSuccess(completion("REJECTED","HARASSMENT","stop"),MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://moderation.test/v1/chat/completions")).andRespond(withSuccess(completion("REJECTED","PERSONAL_DATA","stop"),MediaType.APPLICATION_JSON));
        assertThat(provider.inspect("test abuse").status()).isEqualTo("REJECTED");
        assertThat(provider.inspect("test private data").status()).isEqualTo("REVIEW_REQUIRED");server.verify();
    }
    @Test void truncatedAndContradictoryResponsesCannotApprove()throws Exception {
        var provider=provider("GMS");
        for(String body:new String[]{completion("APPROVED","SAFE","length"),completion("APPROVED","THREAT","stop"),"{}","not json"})
            server.expect(requestTo("https://moderation.test/v1/chat/completions")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        for(int i=0;i<4;i++)assertThat(provider.inspect("test").status()).isEqualTo("REVIEW_REQUIRED");server.verify();
    }
    @Test void quotaAndTransportFailuresGoToReviewWithoutInlineRetries() {
        var provider=provider("GMS");
        server.expect(requestTo("https://moderation.test/v1/chat/completions")).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://moderation.test/v1/chat/completions")).andRespond(withException(new java.net.SocketTimeoutException("must not leak")));
        assertThat(provider.inspect("test").reason()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(provider.inspect("test").reason()).isEqualTo("PROVIDER_UNAVAILABLE");server.verify();
    }
    @Test void openaiDoesNotTreatUnflaggedAsCompleteSpamAndPrivacyValidation() {
        var provider=provider("OPENAI");
        for(boolean flagged:new boolean[]{false,true})server.expect(requestTo("https://moderation.test/v1/moderations"))
            .andExpect(jsonPath("$.model").value("omni-moderation-latest"))
            .andRespond(withSuccess("{\"results\":[{\"flagged\":"+flagged+",\"categories\":{}}]}",MediaType.APPLICATION_JSON));
        assertThat(provider.inspect("test").reason()).isEqualTo("POLICY_SUPPLEMENT_REQUIRED");
        assertThat(provider.inspect("test").reason()).isEqualTo("SAFETY_REVIEW");server.verify();
    }
    @Test void missingKeyMakesNoRequest() {
        var provider=new HttpModerationProvider(builder.build(),json,"GMS","gpt-5-nano","");
        assertThat(provider.inspect("test").reason()).isEqualTo("PROVIDER_NOT_CONFIGURED");server.verify();
    }
}
