package com.CampusToursLive.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.review.ReviewService;
import com.CampusToursLive.web.ReviewReadController;
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
 * The public review read surfaces must be reachable anonymously (no bearer), like the tour catalog
 * — they are marketplace content an anonymous visitor reads before signing in. Mirrors {@link
 * PublicHeadAccessTest}: a GET/HEAD with no auth must NOT be answered with 401 by the filter chain.
 */
@WebMvcTest(controllers = ReviewReadController.class)
@Import(SecurityConfig.class)
class ReviewReadPublicAccessTest {

    @Autowired private MockMvc mvc;

    /** Mocked so the filter chain builds without reaching Google's JWKS. */
    @MockitoBean private JwtDecoder jwtDecoder;

    @MockitoBean private ReviewService reviewService;

    @Test
    void guideReviews_areReadableAnonymously() throws Exception {
        when(reviewService.getGuideReviews(
                        any(UUID.class),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/guides/{id}/reviews", UUID.randomUUID())).andExpect(status().isOk());
    }

    @Test
    void offeringReviews_areReadableAnonymously() throws Exception {
        when(reviewService.getOfferingReviews(
                        any(UUID.class),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/offerings/{id}/reviews", UUID.randomUUID())).andExpect(status().isOk());
    }
}
