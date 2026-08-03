package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

import com.CampusToursLive.domain.reschedule.RescheduleService;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.security.ProvisionedAccount;
import com.CampusToursLive.web.dto.CreateRescheduleProposalRequest;
import com.CampusToursLive.web.dto.RescheduleProposalResponse;
import java.time.Instant;
import java.util.Set;
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
    void propose_delegates() {
        UUID userId = UUID.randomUUID();
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
        when(currentUser.requireProvisioned())
                .thenReturn(
                        new ProvisionedAccount(
                                userId,
                                "sub",
                                "a@b.c",
                                "A",
                                "B",
                                "A B",
                                AccountStatus.ACTIVE,
                                null,
                                Instant.parse("2024-01-01T00:00:00Z"),
                                Set.of()));
        when(rescheduleService.propose(userId, bookingId, req)).thenReturn(detail);
        assertSame(
                detail,
                new RescheduleController(currentUser, rescheduleService)
                        .propose(bookingId, req)
                        .data());
    }
}
