package com.CampusToursLive.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.web.PublicGuideController;
import com.CampusToursLive.web.dto.PublicGuideProfileResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The public guide profile must be reachable anonymously (no bearer), like the tour catalog.
 * Mirrors {@link PublicHeadAccessTest}: a GET with no auth must NOT be answered with 401 by the
 * filter chain.
 */
@WebMvcTest(controllers = PublicGuideController.class)
@Import(SecurityConfig.class)
class PublicGuideAccessTest {

    @Autowired private MockMvc mvc;

    /** Mocked so the filter chain builds without reaching Google's JWKS. */
    @MockitoBean private JwtDecoder jwtDecoder;

    @MockitoBean private GuideService guideService;

    @Test
    void guideProfile_isReadableAnonymously() throws Exception {
        UUID guideId = UUID.randomUUID();
        when(guideService.getPublicProfile(any(UUID.class)))
                .thenReturn(
                        new PublicGuideProfileResponse(
                                guideId.toString(), "Maya", "bio", List.of("en-US"), List.of()));
        mvc.perform(get("/guides/{id}", guideId)).andExpect(status().isOk());
    }
}
