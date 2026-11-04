package com.tagme.participation;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParticipationControllerTest {
    @Test void expiredIntentIsRemovedAndCannotReachTagResolution() {
        var service=mock(ParticipationService.class);var controller=new ParticipationController(service);
        var request=new MockHttpServletRequest();request.getSession().setAttribute("TAGME_PENDING_TAG",new ParticipationController.PendingTag("hash-only",Instant.now().minusSeconds(1)));
        assertThat(controller.pending(request).present()).isFalse();
        assertThat(request.getSession().getAttribute("TAGME_PENDING_TAG")).isNull();
        assertThatThrownBy(() -> controller.resume(null,request)).isInstanceOf(com.tagme.auth.AuthException.class)
                .satisfies(error -> assertThat(((com.tagme.auth.AuthException)error).status()).isEqualTo(404));
        verifyNoInteractions(service);
    }
}
