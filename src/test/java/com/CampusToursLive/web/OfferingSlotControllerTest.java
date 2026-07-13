package com.CampusToursLive.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.booking.SlotGenerationService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.SlotResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice test for {@link OfferingSlotController} (CTL-54 Task 8): real routing, the {data,
 * meta} envelope, and that domain exceptions map to RFC 7807 problem+json via {@code
 * GlobalExceptionHandler}. Mirrors {@code AvailabilityControllerTest}: {@link
 * SlotGenerationService} is mocked, so this test only exercises the controller/role/HTTP-status
 * wiring, never real slicing/subtraction (covered by {@code SlotGenerationServiceIntegrationTest}).
 */
@WebMvcTest(
        controllers = OfferingSlotController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
class OfferingSlotControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private SlotGenerationService slots;

    private static UserEntity user() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        return u;
    }

    @Test
    void getSlots_returnsEnvelope_whenAuthorized() throws Exception {
        UUID offeringId = UUID.randomUUID();
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(slots.getBookableSlots(offeringId, null, null))
                .thenReturn(
                        List.of(
                                new SlotResponse(
                                        Instant.parse("2026-07-14T10:00:00Z"),
                                        Instant.parse("2026-07-14T11:00:00Z"))));

        mvc.perform(get("/offerings/{id}/slots", offeringId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].startAt").value("2026-07-14T10:00:00Z"))
                .andExpect(jsonPath("$.data[0].endAt").value("2026-07-14T11:00:00Z"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void getSlots_passesWindowParams_whenProvided() throws Exception {
        UUID offeringId = UUID.randomUUID();
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(slots.getBookableSlots(eq(offeringId), eq("2026-07-01"), eq("2026-08-01")))
                .thenReturn(List.of());

        mvc.perform(
                        get("/offerings/{id}/slots", offeringId)
                                .param("from", "2026-07-01")
                                .param("to", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getSlots_200_emptyList_whenOfferingHasNoOccurrences() throws Exception {
        UUID offeringId = UUID.randomUUID();
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(slots.getBookableSlots(offeringId, null, null)).thenReturn(List.of());

        mvc.perform(get("/offerings/{id}/slots", offeringId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getSlots_403_whenNonParticipantCaller() throws Exception {
        UUID offeringId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.PARTICIPANT))
                .thenThrow(new ForbiddenException("Missing required role: PARTICIPANT"));

        mvc.perform(get("/offerings/{id}/slots", offeringId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getSlots_404_whenOfferingNotFoundOrNotActive() throws Exception {
        UUID offeringId = UUID.randomUUID();
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        when(slots.getBookableSlots(offeringId, null, null))
                .thenThrow(new NotFoundException("Tour not found"));

        mvc.perform(get("/offerings/{id}/slots", offeringId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
