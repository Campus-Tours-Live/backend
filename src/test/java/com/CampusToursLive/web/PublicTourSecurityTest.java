package com.CampusToursLive.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourDiscoverySort;
import com.CampusToursLive.security.SecurityConfig;
import com.CampusToursLive.web.dto.TourDetailResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies the anonymous marketplace allowlist without weakening adjacent protected APIs. */
@WebMvcTest(controllers = TourController.class)
@Import(SecurityConfig.class)
class PublicTourSecurityTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private TourDiscoveryService discovery;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void catalog_isAvailableWithoutAuthentication() throws Exception {
        when(discovery.list(null, null, "", TourDiscoverySort.RECOMMENDED, 20))
                .thenReturn(List.of());

        mvc.perform(get("/tours")).andExpect(status().isOk());
    }

    @Test
    void tourDetail_isAvailableWithoutAuthentication() throws Exception {
        UUID tourId = UUID.randomUUID();
        when(discovery.getById(tourId))
                .thenReturn(
                        new TourDetailResponse(
                                tourId.toString(),
                                "Campus walk",
                                "campus-walk",
                                "GENERAL_CAMPUS",
                                "Description",
                                List.of("en-US"),
                                "u1",
                                "North Coast University",
                                "north-coast",
                                "Arcata",
                                "CA",
                                "g1",
                                "Maya Chen",
                                "Bio",
                                60,
                                4200L,
                                "USD",
                                4.5,
                                12));

        mvc.perform(get("/tours/{tourId}", tourId)).andExpect(status().isOk());
    }

    @Test
    void availability_remainsProtectedWithoutAuthentication() throws Exception {
        mvc.perform(get("/availability")).andExpect(status().isUnauthorized());
    }

    @Test
    void participantSlots_remainProtectedWithoutAuthentication() throws Exception {
        mvc.perform(get("/offerings/{offeringId}/slots", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookings_remainProtectedWithoutAuthentication() throws Exception {
        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
