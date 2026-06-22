package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.GuideDecisionRequest;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AdminController.decide — the ADMIN gate (requireRole reads user_roles, independent of the
 * caller's active consumer role) and delegation to GuideService.reviewApplication. A non-admin
 * caller must be rejected BEFORE any review happens.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock CurrentUser currentUser;
    @Mock GuideService guideService;

    private AdminController controller() {
        return new AdminController(currentUser, guideService);
    }

    @Test
    void decide_delegatesToReview_whenAdmin() {
        UUID guideUserId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.ADMIN)).thenReturn(new UserEntity());
        when(guideService.reviewApplication(eq(guideUserId), eq("APPROVED")))
                .thenReturn(
                        new GuideProfileResponse(
                                guideUserId.toString(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "APPROVED",
                                "VERIFIED"));

        var envelope = controller().decide(guideUserId, new GuideDecisionRequest("APPROVED"));

        assertEquals("APPROVED", envelope.data().applicationStatus());
    }

    @Test
    void decide_403_andNoReview_whenNotAdmin() {
        UUID guideUserId = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.ADMIN))
                .thenThrow(new ForbiddenException("Missing required role: ADMIN"));

        assertThrows(
                ForbiddenException.class,
                () -> controller().decide(guideUserId, new GuideDecisionRequest("APPROVED")));
        verify(guideService, never()).reviewApplication(any(), any());
    }
}
