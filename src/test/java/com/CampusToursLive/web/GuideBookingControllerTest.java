package com.CampusToursLive.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.booking.BookingService;
import com.CampusToursLive.domain.booking.BookingService.GuideBookingFilter;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.CancelBookingRequest;
import com.CampusToursLive.web.dto.GuideBookingDetailResponse;
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
        controllers = GuideBookingController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
class GuideBookingControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private BookingService bookings;

    private static UserEntity user() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        return u;
    }

    private static GuideBookingDetailResponse detail(String id, String status) {
        return new GuideBookingDetailResponse(
                id,
                "CTL-2026-00042",
                status,
                "2026-08-01T15:00:00Z",
                "o1",
                "Campus Walk",
                "Sam Rivera",
                null,
                "2026-07-30T15:00:00Z",
                "Test University",
                60,
                4200L,
                "USD",
                null);
    }

    @Test
    void list_returnsEnvelope_whenAuthorized() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.listForGuide(u, GuideBookingFilter.PENDING))
                .thenReturn(List.of(detail("b1", "WAITING_FOR_GUIDE")));

        mvc.perform(get("/guide/bookings").param("filter", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("b1"))
                .andExpect(jsonPath("$.data[0].participantName").value("Sam Rivera"));
    }

    @Test
    void accept_returnsConfirmed() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.acceptBooking(u, id)).thenReturn(detail(id.toString(), "CONFIRMED"));

        mvc.perform(post("/guide/bookings/" + id + "/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void decline_passesOptionalBody() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.declineBooking(eq(u), eq(id), any(CancelBookingRequest.class)))
                .thenReturn(detail(id.toString(), "DECLINED"));

        mvc.perform(
                        post("/guide/bookings/" + id + "/decline")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Busy that day\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));
    }

    @Test
    void decline_allowsEmptyBody() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.declineBooking(eq(u), eq(id), isNull()))
                .thenReturn(detail(id.toString(), "DECLINED"));

        mvc.perform(post("/guide/bookings/" + id + "/decline")).andExpect(status().isOk());

        verify(bookings).declineBooking(eq(u), eq(id), isNull());
    }

    @Test
    void accept_mapsNotFoundTo404() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.acceptBooking(u, id)).thenThrow(new NotFoundException("Booking not found"));

        mvc.perform(post("/guide/bookings/" + id + "/accept")).andExpect(status().isNotFound());
    }

    @Test
    void accept_mapsValidationTo422() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.acceptBooking(u, id))
                .thenThrow(
                        new ValidationException(
                                "The response window for this booking has expired"));

        mvc.perform(post("/guide/bookings/" + id + "/accept"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void list_defaultsFilterToAll_whenParamOmitted() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.listForGuide(u, GuideBookingFilter.ALL)).thenReturn(List.of());

        mvc.perform(get("/guide/bookings")).andExpect(status().isOk());

        verify(bookings).listForGuide(u, GuideBookingFilter.ALL);
    }

    @Test
    void list_mapsInvalidFilterTo422() throws Exception {
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(user());

        mvc.perform(get("/guide/bookings").param("filter", "nope"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void get_returnsEnvelope_whenAuthorized() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.getForGuide(u, id)).thenReturn(detail(id.toString(), "CONFIRMED"));

        mvc.perform(get("/guide/bookings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.bookingNumber").value("CTL-2026-00042"));
    }

    @Test
    void complete_returnsCompleted() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.completeBooking(u, id)).thenReturn(detail(id.toString(), "COMPLETED"));

        mvc.perform(post("/guide/bookings/" + id + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void noShow_passesOptionalBody() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.markParticipantNoShow(eq(u), eq(id), any(CancelBookingRequest.class)))
                .thenReturn(detail(id.toString(), "PARTICIPANT_NO_SHOW"));

        mvc.perform(
                        post("/guide/bookings/" + id + "/no-show")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Never arrived\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTICIPANT_NO_SHOW"));
    }

    @Test
    void list_past_filter() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.listForGuide(u, GuideBookingFilter.PAST))
                .thenReturn(List.of(detail("b-past", "COMPLETED")));

        mvc.perform(get("/guide/bookings").param("filter", "past"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
    }

    @Test
    void get_mapsNotFound() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(bookings.getForGuide(u, id)).thenThrow(new NotFoundException("Booking not found"));

        mvc.perform(get("/guide/bookings/" + id)).andExpect(status().isNotFound());
    }
}
