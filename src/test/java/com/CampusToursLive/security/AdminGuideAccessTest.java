package com.CampusToursLive.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.guide.GuideDecisionService;
import com.CampusToursLive.web.AdminGuideController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Admin guide review is never public: POST needs a bearer token. */
@WebMvcTest(controllers = AdminGuideController.class)
@Import(SecurityConfig.class)
class AdminGuideAccessTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private GuideDecisionService decisions;

    @Test
    void anonymousDecision_is401() throws Exception {
        mvc.perform(
                        post("/admin/guides/" + UUID.randomUUID() + "/decision")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isUnauthorized());
    }
}
