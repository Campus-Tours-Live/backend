package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GuideController — thin adapter: resolves the current user and delegates to GuideService, wrapping
 * the result in the {@code {data, meta}} envelope. These tests verify the delegation (the business
 * rules themselves live in, and are tested via, GuideService).
 */
@ExtendWith(MockitoExtension.class)
class GuideControllerTest {

    @Mock CurrentUser currentUser;
    @Mock GuideService guideService;

    private GuideController controller() {
        return new GuideController(currentUser, guideService);
    }

    private static GuideProfileResponse response() {
        return new GuideProfileResponse(
                "uid", null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    @Test
    void getProfile_delegatesAndWrapsInEnvelope() {
        UserEntity u = new UserEntity();
        GuideProfileResponse resp = response();
        when(currentUser.require()).thenReturn(u);
        when(guideService.getProfile(u)).thenReturn(resp);

        assertSame(resp, controller().getProfile().data());
    }

    @Test
    void updateProfile_delegatesAndWrapsInEnvelope() {
        UserEntity u = new UserEntity();
        GuideProfileUpdateRequest req =
                new GuideProfileUpdateRequest(
                        null, null, null, null, null, null, null, null, null, null, null);
        GuideProfileResponse resp = response();
        when(currentUser.require()).thenReturn(u);
        when(guideService.updateProfile(u, req)).thenReturn(resp);

        assertSame(resp, controller().updateProfile(req).data());
    }
}
