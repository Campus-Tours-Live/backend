package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.reschedule.RescheduleService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.CreateRescheduleProposalRequest;
import com.CampusToursLive.web.dto.RescheduleProposalResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RescheduleController — thin adapter: resolves the authenticated user (either party of the booking
 * may propose), delegates to RescheduleService, wraps the result in the {@code {data, meta}}
 * envelope.
 */
@ExtendWith(MockitoExtension.class)
class RescheduleControllerTest {

    @Mock CurrentUser currentUser;
    @Mock RescheduleService rescheduleService;

    private RescheduleController controller() {
        return new RescheduleController(currentUser, rescheduleService);
    }

    @Test
    void propose_requiresAuthenticatedUser_andWrapsResultInEnvelope() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        UUID bookingId = UUID.randomUUID();
        CreateRescheduleProposalRequest req =
                new CreateRescheduleProposalRequest("2026-08-05T17:00:00Z", null, null);
        RescheduleProposalResponse detail =
                new RescheduleProposalResponse(
                        UUID.randomUUID().toString(),
                        bookingId.toString(),
                        "PARTICIPANT",
                        "PENDING_COUNTERPARTY",
                        "2026-08-05T17:00:00Z",
                        "2026-08-05T18:00:00Z",
                        0L,
                        0L,
                        "2026-08-03T17:00:00Z");

        when(currentUser.require()).thenReturn(u);
        when(rescheduleService.propose(u, bookingId, req)).thenReturn(detail);

        assertSame(detail, controller().propose(bookingId, req).data());
        verify(currentUser).require();
        verify(currentUser, never()).requireRole(any());
    }
}
