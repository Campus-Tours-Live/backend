package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.participant.ParticipantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.ParticipantProfileUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ParticipantController — thin adapter: resolves the current user and delegates to
 * ParticipantService, wrapping the result in the {@code {data, meta}} envelope. These tests verify
 * the delegation (the business rules live in, and are tested via, ParticipantService).
 */
@ExtendWith(MockitoExtension.class)
class ParticipantControllerTest {

    @Mock CurrentUser currentUser;
    @Mock ParticipantService participantService;

    private ParticipantController controller() {
        return new ParticipantController(currentUser, participantService);
    }

    private static ParticipantProfileResponse response() {
        return new ParticipantProfileResponse(
                "uid", null, null, null, null, null, null, null, null, null, null, null, null,
                null);
    }

    @Test
    void getProfile_delegatesAndWrapsInEnvelope() {
        UserEntity u = new UserEntity();
        ParticipantProfileResponse resp = response();
        when(currentUser.require()).thenReturn(u);
        when(participantService.getProfile(u)).thenReturn(resp);

        assertSame(resp, controller().getProfile().data());
    }

    @Test
    void updateProfile_delegatesAndWrapsInEnvelope() {
        UserEntity u = new UserEntity();
        ParticipantProfileUpdateRequest req =
                new ParticipantProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null);
        ParticipantProfileResponse resp = response();
        when(currentUser.require()).thenReturn(u);
        when(participantService.updateProfile(u, req)).thenReturn(resp);

        assertSame(resp, controller().updateProfile(req).data());
    }
}
