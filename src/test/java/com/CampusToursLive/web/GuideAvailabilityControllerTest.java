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

import com.CampusToursLive.domain.availability.GuideAvailabilityService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.AvailabilitySummaryResponse;
import com.CampusToursLive.web.dto.BookingSettingsResponse;
import com.CampusToursLive.web.dto.CreateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.CreateAvailabilityRuleRequest;
import com.CampusToursLive.web.dto.UpdateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.UpdateAvailabilityRuleRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = GuideAvailabilityController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
class GuideAvailabilityControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private GuideAvailabilityService availability;

    private static UserEntity user() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        return u;
    }

    @Test
    void getSummary_returnsEnvelope_whenAuthorized() throws Exception {
        UserEntity u = user();
        AvailabilitySummaryResponse summary =
                new AvailabilitySummaryResponse(
                        List.of(),
                        List.of(),
                        new BookingSettingsResponse(
                                "MANUAL", 90, 1440, 30, 0, 15, List.of(60), "America/Los_Angeles"));
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.getSummary(u)).thenReturn(summary);

        mvc.perform(get("/guide/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingSettings.minNoticeMin").value(1440))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void getSummary_403_whenGuideRoleMissing() throws Exception {
        when(currentUser.requireRole(UserRole.GUIDE))
                .thenThrow(new ForbiddenException("Missing required role: GUIDE"));

        mvc.perform(get("/guide/availability"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void createRule_acceptsJsonBody() throws Exception {
        UserEntity u = user();
        AvailabilityRuleResponse row =
                new AvailabilityRuleResponse(
                        "r1",
                        1,
                        "10:00",
                        "13:00",
                        "America/Los_Angeles",
                        "2026-06-01",
                        null,
                        true,
                        null);
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.createRule(eq(u), any(CreateAvailabilityRuleRequest.class)))
                .thenReturn(row);

        mvc.perform(
                        post("/guide/availability/rules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "dayOfWeek": 1,
                                          "startLocal": "10:00",
                                          "endLocal": "13:00",
                                          "timezone": "America/Los_Angeles",
                                          "effectiveFrom": "2026-06-01"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("r1"));
    }

    @Test
    void deleteRule_returnsNullData() throws Exception {
        UserEntity u = user();
        UUID ruleId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);

        mvc.perform(delete("/guide/availability/rules/{id}", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void updateRule_acceptsJsonBody() throws Exception {
        UserEntity u = user();
        UUID ruleId = UUID.randomUUID();
        AvailabilityRuleResponse row =
                new AvailabilityRuleResponse(
                        ruleId.toString(),
                        2,
                        "14:00",
                        "17:00",
                        "America/Los_Angeles",
                        "2026-06-01",
                        null,
                        true,
                        null);
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.updateRule(eq(u), eq(ruleId), any(UpdateAvailabilityRuleRequest.class)))
                .thenReturn(row);

        mvc.perform(
                        patch("/guide/availability/rules/{id}", ruleId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"startLocal\":\"14:00\",\"endLocal\":\"17:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startLocal").value("14:00"));
    }

    @Test
    void createException_acceptsJsonBody() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.createException(eq(u), any(CreateAvailabilityExceptionRequest.class)))
                .thenReturn(
                        new com.CampusToursLive.web.dto.AvailabilityExceptionResponse(
                                "e1", "2026-07-04", "UNAVAILABLE_ALL_DAY", null, null, null, null));

        mvc.perform(
                        post("/guide/availability/exceptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "exceptionDate": "2026-07-04",
                                          "type": "UNAVAILABLE_ALL_DAY"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("e1"));
    }

    @Test
    void updateException_acceptsJsonBody() throws Exception {
        UserEntity u = user();
        UUID exceptionId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.updateException(
                        eq(u), eq(exceptionId), any(UpdateAvailabilityExceptionRequest.class)))
                .thenReturn(
                        new com.CampusToursLive.web.dto.AvailabilityExceptionResponse(
                                exceptionId.toString(),
                                "2026-07-04",
                                "UNAVAILABLE_RANGE",
                                "10:00",
                                "12:00",
                                null,
                                null));

        mvc.perform(
                        patch("/guide/availability/exceptions/{id}", exceptionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"startLocal\":\"10:00\",\"endLocal\":\"12:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("UNAVAILABLE_RANGE"));
    }

    @Test
    void deleteException_returnsNullData() throws Exception {
        UserEntity u = user();
        UUID exceptionId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);

        mvc.perform(delete("/guide/availability/exceptions/{id}", exceptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void updateBookingSettings_returnsUpdatedSettings() throws Exception {
        UserEntity u = user();
        BookingSettingsResponse settings =
                new BookingSettingsResponse(
                        "MANUAL", 90, 720, 30, 0, 15, List.of(60), "America/Los_Angeles");
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(availability.updateBookingSettings(eq(u), any())).thenReturn(settings);

        mvc.perform(
                        patch("/guide/availability/booking-settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"minNoticeMin\":720}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minNoticeMin").value(720));
    }
}
