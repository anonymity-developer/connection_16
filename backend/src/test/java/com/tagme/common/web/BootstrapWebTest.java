package com.tagme.common.web;

import com.tagme.common.config.SecurityConfig;
import com.tagme.auth.AuthRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MetaController.class, CsrfController.class})
@Import(SecurityConfig.class)
class BootstrapWebTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean AuthRepository accounts;

    @Test
    void metadataIsPublicAndHasTwoSeparateSMissions() throws Exception {
        mvc.perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("bootstrap"))
                .andExpect(jsonPath("$.missions.length()").value(5))
                .andExpect(jsonPath("$.missions[0].code").value("SHOW_ME"))
                .andExpect(jsonPath("$.missions[1].code").value("SYNC_ME"))
                .andExpect(jsonPath("$.missions[4].letter").value("Y"));
    }

    @Test
    void privateApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void mutationWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/v1/wishes"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointMaterializesCookieAndToken() throws Exception {
        mvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
