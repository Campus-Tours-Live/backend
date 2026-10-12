package com.CampusToursLive.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.web.PublicGuideToursController;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A guide's public tour list must be reachable anonymously (no bearer), like the tour catalog.
 * Mirrors {@link PublicHeadAccessTest}: a GET with no auth must NOT be answered with 401.
 */
@WebMvcTest(controllers = PublicGuideToursController.class)
@Import(SecurityConfig.class)
class PublicGuideToursAccessTest {

    @Autowired private MockMvc mvc;

    /** Mocked so the filter chain builds without reaching Google's JWKS. */
    @MockitoBean private JwtDecoder jwtDecoder;

    @MockitoBean private TourDiscoveryService discovery;

    @Test
    void guideTours_areReadableAnonymously() throws Exception {
        when(discovery.listByGuide(any(UUID.class), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/guides/{id}/tours", UUID.randomUUID())).andExpect(status().isOk());
    }
}
