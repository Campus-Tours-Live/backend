package com.CampusToursLive.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.availability.AvailabilityPreviewService;
import com.CampusToursLive.domain.availability.AvailabilityReadService;
import com.CampusToursLive.domain.availability.AvailabilityWriteService;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.AffectedBookingResponse;
import com.CampusToursLive.web.dto.AvailabilityExceptionResponse;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.GuideBookingSettingsResponse;
import com.CampusToursLive.web.dto.OverridePreviewResponse;
import com.CampusToursLive.web.dto.ResolvedAvailabilityResponse;
import com.CampusToursLive.web.dto.ResolvedOccurrence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice test for {@link AvailabilityController}: real routing, {@code @RequestBody}
 * binding, the {data, meta} envelope, and that domain exceptions ({@link ForbiddenException},
 * {@link NotFoundException}, {@link ValidationException}) map to RFC 7807 problem+json via {@code
 * GlobalExceptionHandler}. Mirrors {@code GuideOfferingControllerTest}: {@link
 * AvailabilityWriteService} is mocked, so this test only exercises the controller/role/HTTP-status
 * wiring, never real persistence or rematerialize (covered separately by {@code
 * AvailabilityWriteServiceIntegrationTest}).
 */
@WebMvcTest(
        controllers = AvailabilityController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
class AvailabilityControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private AvailabilityWriteService availability;
    @MockitoBean private AvailabilityReadService availabilityRead;
    @MockitoBean private AvailabilityPreviewService availabilityPreview;
    @MockitoBean private GuideProfileRepository guides;

    private static UserEntity user() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        return u;
    }

    private static AvailabilityRuleResponse rule(UUID id) {
        return new AvailabilityRuleResponse(
                id.toString(), 1, "09:00", 60, "America/Los_Angeles", "2026-07-11", null, true);
    }

    private static AvailabilityExceptionResponse exception(UUID id) {
        return new AvailabilityExceptionResponse(
                id.toString(), "2026-07-12", "ADDITIONAL", "10:00", 60, "extra hours");
    }

    private static GuideBookingSettingsResponse settings(UUID guideId, String timezone) {
        return new GuideBookingSettingsResponse(
                guideId.toString(),
                "MANUAL",
                90,
                1440,
                30,
                0,
                15,
                List.of(30, 45, 60, 90),
                timezone,
                "2026-07-11T00:00:00Z");
    }

    // ---------------------------------------------------------------------
    // Resolved availability (CTL-54 Task 5b).
    // ---------------------------------------------------------------------

    @Test
    void getResolvedAvailability_returnsEnvelope_whenAuthorized() throws Exception {
        UserEntity u = user();
        UUID ruleId = UUID.randomUUID();
        ResolvedAvailabilityResponse resolved =
                new ResolvedAvailabilityResponse(
                        List.of(rule(ruleId)),
                        List.of(
                                new ResolvedOccurrence(
                                        Instant.parse("2026-07-13T16:00:00Z"),
                                        Instant.parse("2026-07-13T17:00:00Z"))),
                        List.of("2026-03-08"),
                        true,
                        true);
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availabilityRead.getResolvedAvailability(u, null, null)).thenReturn(resolved);

        mvc.perform(get("/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules[0].id").value(ruleId.toString()))
                .andExpect(jsonPath("$.data.occurrences[0].startAt").value("2026-07-13T16:00:00Z"))
                .andExpect(jsonPath("$.data.occurrences[0].endAt").value("2026-07-13T17:00:00Z"))
                .andExpect(jsonPath("$.data.dstGapDays[0]").value("2026-03-08"))
                .andExpect(jsonPath("$.data.bookable").value(true))
                .andExpect(jsonPath("$.data.hasWeeklyHours").value(true))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void getResolvedAvailability_carriesReadinessSignals_whenNotYetReady() throws Exception {
        UserEntity u = user();
        // Guide has occurrences to book but no active weekly rule -> bookable true, no weekly
        // hours.
        ResolvedAvailabilityResponse resolved =
                new ResolvedAvailabilityResponse(List.of(), List.of(), List.of(), true, false);
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availabilityRead.getResolvedAvailability(u, null, null)).thenReturn(resolved);

        mvc.perform(get("/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookable").value(true))
                .andExpect(jsonPath("$.data.hasWeeklyHours").value(false));
    }

    @Test
    void getResolvedAvailability_passesWindowParams_whenProvided() throws Exception {
        UserEntity u = user();
        ResolvedAvailabilityResponse resolved =
                new ResolvedAvailabilityResponse(List.of(), List.of(), List.of(), false, false);
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availabilityRead.getResolvedAvailability(u, "2026-07-01", "2026-08-01"))
                .thenReturn(resolved);

        mvc.perform(get("/availability").param("from", "2026-07-01").param("to", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules").isEmpty())
                .andExpect(jsonPath("$.data.occurrences").isEmpty())
                .andExpect(jsonPath("$.data.dstGapDays").isEmpty());
    }

    @Test
    void getResolvedAvailability_403_whenNonGuideCaller() throws Exception {
        when(currentUser.requireRole(UserRole.GUIDE))
                .thenThrow(new ForbiddenException("Missing required role: GUIDE"));

        mvc.perform(get("/availability"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getResolvedAvailability_422_whenWindowMalformed() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availabilityRead.getResolvedAvailability(u, "not-a-date", null))
                .thenThrow(
                        new ValidationException(
                                "Invalid from (expected e.g. \"2026-07-11\"): not-a-date"));

        mvc.perform(get("/availability").param("from", "not-a-date"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void getResolvedAvailability_422_whenToNotAfterFrom() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availabilityRead.getResolvedAvailability(u, "2026-07-11", "2026-07-01"))
                .thenThrow(new ValidationException("to must be after from"));

        mvc.perform(get("/availability").param("from", "2026-07-11").param("to", "2026-07-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    // ---------------------------------------------------------------------
    // Date-specific override dry-run preview (CTL-54 v2.1 Task 4).
    // ---------------------------------------------------------------------

    @Test
    void getOverridePreview_returnsEnvelope_whenAuthorized() throws Exception {
        UserEntity u = user();
        UUID guideId = UUID.randomUUID();
        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(guideId);
        guide.setUserId(u.getId());
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(guides.findByUserId(u.getId())).thenReturn(Optional.of(guide));

        OverridePreviewResponse preview =
                new OverridePreviewResponse(
                        List.of(
                                new OverridePreviewResponse.DatePreview(
                                        "2026-07-12",
                                        List.of(
                                                new ResolvedOccurrence(
                                                        Instant.parse("2026-07-12T16:00:00Z"),
                                                        Instant.parse("2026-07-12T16:30:00Z"))),
                                        List.of(
                                                new OverridePreviewResponse.TrimmedSegment(
                                                        "ADDITIONAL", "09:00", 60)))),
                        true,
                        null);
        when(availabilityPreview.preview(eq(guideId), any())).thenReturn(preview);

        mvc.perform(
                        get("/availability/preview")
                                .param("dateFrom", "2026-07-12")
                                .param("dateTo", "2026-07-12")
                                .param("kind", "UNAVAILABLE")
                                .param("startLocal", "09:30")
                                .param("windowMin", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-07-12"))
                .andExpect(
                        jsonPath("$.data.days[0].resultingWindows[0].startAt")
                                .value("2026-07-12T16:00:00Z"))
                .andExpect(jsonPath("$.data.days[0].trimmed[0].kind").value("ADDITIONAL"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void getOverridePreview_403_whenNonGuideCaller() throws Exception {
        when(currentUser.requireRole(UserRole.GUIDE))
                .thenThrow(new ForbiddenException("Missing required role: GUIDE"));

        mvc.perform(
                        get("/availability/preview")
                                .param("dateFrom", "2026-07-12")
                                .param("dateTo", "2026-07-12")
                                .param("kind", "UNAVAILABLE")
                                .param("startLocal", "09:30")
                                .param("windowMin", "90"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getOverridePreview_422_whenNoGuideProfile() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(guides.findByUserId(u.getId())).thenReturn(Optional.empty());

        mvc.perform(
                        get("/availability/preview")
                                .param("dateFrom", "2026-07-12")
                                .param("dateTo", "2026-07-12")
                                .param("kind", "UNAVAILABLE")
                                .param("startLocal", "09:30")
                                .param("windowMin", "90"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void getOverridePreview_422_whenServiceRejectsCrossMidnightOverride() throws Exception {
        UserEntity u = user();
        UUID guideId = UUID.randomUUID();
        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(guideId);
        guide.setUserId(u.getId());
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(guides.findByUserId(u.getId())).thenReturn(Optional.of(guide));
        when(availabilityPreview.preview(eq(guideId), any()))
                .thenThrow(new ValidationException("This time off cannot cross midnight."));

        mvc.perform(
                        get("/availability/preview")
                                .param("dateFrom", "2026-07-12")
                                .param("dateTo", "2026-07-12")
                                .param("kind", "UNAVAILABLE")
                                .param("startLocal", "22:00")
                                .param("windowMin", "240"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void getOverridePreview_422_whenServiceRejectsOver366DayRange() throws Exception {
        UserEntity u = user();
        UUID guideId = UUID.randomUUID();
        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(guideId);
        guide.setUserId(u.getId());
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(guides.findByUserId(u.getId())).thenReturn(Optional.of(guide));
        when(availabilityPreview.preview(eq(guideId), any()))
                .thenThrow(new ValidationException("Date range too large (max 366 days)."));

        mvc.perform(
                        get("/availability/preview")
                                .param("dateFrom", "2026-07-12")
                                .param("dateTo", "2027-07-14")
                                .param("kind", "UNAVAILABLE")
                                .param("startLocal", "09:00")
                                .param("windowMin", "60"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    // ---------------------------------------------------------------------
    // Rules.
    // ---------------------------------------------------------------------

    @Test
    void listRules_returnsEnvelope_whenAuthorized() throws Exception {
        UserEntity u = user();
        UUID ruleId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.listRules(u)).thenReturn(List.of(rule(ruleId)));

        mvc.perform(get("/availability/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ruleId.toString()))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void listRules_403_whenNonGuideCaller() throws Exception {
        when(currentUser.requireRole(UserRole.GUIDE))
                .thenThrow(new ForbiddenException("Missing required role: GUIDE"));

        mvc.perform(get("/availability/rules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Missing required role: GUIDE"));
    }

    @Test
    void createRule_returnsEnvelope_whenValid() throws Exception {
        UserEntity u = user();
        UUID ruleId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.createRule(eq(u), any())).thenReturn(rule(ruleId));

        mvc.perform(
                        post("/availability/rules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dayOfWeek\":1,\"startLocal\":\"09:00\",\"windowMin\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ruleId.toString()))
                .andExpect(jsonPath("$.data.timezone").value("America/Los_Angeles"))
                // CTL-54 Task 7: no affected bookings were stubbed -> an empty warning list, and
                // the plain "data" contract (asserted above) is completely untouched by its
                // presence.
                .andExpect(jsonPath("$.affectedBookings").isArray())
                .andExpect(jsonPath("$.affectedBookings").isEmpty())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void createRule_surfacesAffectedBookings_whenEditUncoveredAnExistingBooking() throws Exception {
        UserEntity u = user();
        UUID ruleId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.createRule(eq(u), any())).thenReturn(rule(ruleId));
        AffectedBookingResponse affected =
                new AffectedBookingResponse(
                        bookingId.toString(),
                        "BK-7F3K2M9QX1",
                        "2026-07-13T19:00:00Z",
                        "2026-07-13T20:00:00Z",
                        "CONFIRMED");
        when(availability.findAffectedBookings(u)).thenReturn(List.of(affected));

        mvc.perform(
                        post("/availability/rules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dayOfWeek\":1,\"startLocal\":\"09:00\",\"windowMin\":60}"))
                .andExpect(status().isOk())
                // The write's normal payload ("data") is unaffected by the warning.
                .andExpect(jsonPath("$.data.id").value(ruleId.toString()))
                .andExpect(jsonPath("$.affectedBookings[0].bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.affectedBookings[0].bookingNumber").value("BK-7F3K2M9QX1"))
                .andExpect(jsonPath("$.affectedBookings[0].status").value("CONFIRMED"))
                .andExpect(
                        jsonPath("$.affectedBookings[0].scheduledStartAt")
                                .value("2026-07-13T19:00:00Z"));
    }

    @Test
    void createRule_403_whenNonGuideCaller() throws Exception {
        when(currentUser.requireRole(UserRole.GUIDE))
                .thenThrow(new ForbiddenException("Missing required role: GUIDE"));

        mvc.perform(
                        post("/availability/rules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dayOfWeek\":1,\"startLocal\":\"09:00\",\"windowMin\":60}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRule_422_whenWindowMinInvalid() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.createRule(eq(u), any()))
                .thenThrow(new ValidationException("windowMin must be greater than 0"));

        mvc.perform(
                        post("/availability/rules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dayOfWeek\":1,\"startLocal\":\"09:00\",\"windowMin\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("windowMin must be greater than 0"));
    }

    @Test
    void createRule_422_whenDayOfWeekInvalid() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.createRule(eq(u), any()))
                .thenThrow(
                        new ValidationException(
                                "dayOfWeek must be between 0 (Sunday) and 6 (Saturday)"));

        mvc.perform(
                        post("/availability/rules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dayOfWeek\":9,\"startLocal\":\"09:00\",\"windowMin\":60}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void updateRule_404_whenNotOwned() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.updateRule(eq(u), eq(id), any()))
                .thenThrow(new NotFoundException("Availability rule not found"));

        mvc.perform(
                        patch("/availability/rules/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dayOfWeek\":1,\"startLocal\":\"09:00\",\"windowMin\":60}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateRule_returnsEnvelope_whenOwned() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.updateRule(eq(u), eq(id), any())).thenReturn(rule(id));

        mvc.perform(
                        patch("/availability/rules/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dayOfWeek\":1,\"startLocal\":\"09:00\",\"windowMin\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void deleteRule_404_whenNotOwned() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.deleteRule(u, id))
                .thenThrow(new NotFoundException("Availability rule not found"));

        mvc.perform(delete("/availability/rules/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteRule_returnsRemainingRules_whenOwned() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.deleteRule(u, id)).thenReturn(List.of());

        mvc.perform(delete("/availability/rules/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void deleteRule_surfacesAffectedBookings_whenDeletionUncoveredAnExistingBooking()
            throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.deleteRule(u, id)).thenReturn(List.of());
        when(availability.findAffectedBookings(u))
                .thenReturn(
                        List.of(
                                new AffectedBookingResponse(
                                        bookingId.toString(),
                                        "BK-ABANDONED1",
                                        "2026-07-13T19:00:00Z",
                                        "2026-07-13T20:00:00Z",
                                        "CONFIRMED")));

        mvc.perform(delete("/availability/rules/{id}", id))
                .andExpect(status().isOk())
                // "data" (the remaining-rules list) is still exactly what deleteRule returned.
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.affectedBookings[0].bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.affectedBookings[0].status").value("CONFIRMED"));
    }

    // ---------------------------------------------------------------------
    // Exceptions.
    // ---------------------------------------------------------------------

    @Test
    void listExceptions_returnsEnvelope_whenAuthorized() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.listExceptions(u)).thenReturn(List.of(exception(id)));

        mvc.perform(get("/availability/exceptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].kind").value("ADDITIONAL"));
    }

    @Test
    void createException_returnsEnvelope_whenValid() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.createException(eq(u), any())).thenReturn(exception(id));

        mvc.perform(
                        post("/availability/exceptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"exceptionDate\":\"2026-07-12\",\"kind\":\"ADDITIONAL\","
                                                + "\"startLocal\":\"10:00\",\"windowMin\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void createException_422_whenKindMissing() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.createException(eq(u), any()))
                .thenThrow(new ValidationException("kind is required"));

        mvc.perform(
                        post("/availability/exceptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"exceptionDate\":\"2026-07-12\",\"startLocal\":\"10:00\","
                                                + "\"windowMin\":60}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void updateException_404_whenNotOwned() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.updateException(eq(u), eq(id), any()))
                .thenThrow(new NotFoundException("Availability exception not found"));

        mvc.perform(
                        patch("/availability/exceptions/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"exceptionDate\":\"2026-07-12\",\"kind\":\"ADDITIONAL\","
                                                + "\"startLocal\":\"10:00\",\"windowMin\":60}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteException_404_whenNotOwned() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.deleteException(u, id))
                .thenThrow(new NotFoundException("Availability exception not found"));

        mvc.perform(delete("/availability/exceptions/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void deleteException_returnsRemaining_whenOwned() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.deleteException(u, id)).thenReturn(List.of());

        mvc.perform(delete("/availability/exceptions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---------------------------------------------------------------------
    // Settings.
    // ---------------------------------------------------------------------

    @Test
    void getSettings_returnsEnvelope_whenAuthorized() throws Exception {
        UserEntity u = user();
        UUID guideId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.getSettings(u)).thenReturn(settings(guideId, "America/Los_Angeles"));

        mvc.perform(get("/availability/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guideId").value(guideId.toString()))
                .andExpect(jsonPath("$.data.timezone").value("America/Los_Angeles"))
                .andExpect(jsonPath("$.data.durationsOffered[0]").value(30));
    }

    @Test
    void getSettings_403_whenNonGuideCaller() throws Exception {
        when(currentUser.requireRole(UserRole.GUIDE))
                .thenThrow(new ForbiddenException("Missing required role: GUIDE"));

        mvc.perform(get("/availability/settings")).andExpect(status().isForbidden());
    }

    @Test
    void updateSettings_returnsEnvelope_whenValid() throws Exception {
        UserEntity u = user();
        UUID guideId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.updateSettings(eq(u), any()))
                .thenReturn(settings(guideId, "America/New_York"));

        mvc.perform(
                        patch("/availability/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("America/New_York"));
    }

    @Test
    void updateSettings_422_whenTimezoneInvalid() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.updateSettings(eq(u), any()))
                .thenThrow(new ValidationException("Invalid IANA timezone: Not/AZone"));

        mvc.perform(
                        patch("/availability/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"timezone\":\"Not/AZone\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }
}
