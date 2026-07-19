package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertSame;
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

@ExtendWith(MockitoExtension.class)
class RescheduleControllerTest {

    @Mock CurrentUser currentUser;
    @Mock RescheduleService rescheduleService;

    @Test
    void propose_delegatesAndWraps() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        UUID bookingId = UUID.randomUUID();
        var req = new CreateRescheduleProposalRequest("2026-08-05T17:00:00Z", null, null);
        var detail =
                new RescheduleProposalResponse(
                        "id",
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
        assertSame(
                detail,
                new RescheduleController(currentUser, rescheduleService)
                        .propose(bookingId, req)
                        .data());
    }
}
