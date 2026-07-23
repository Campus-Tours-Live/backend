package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideEarningsService;
import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.GuideDashboardStatsResponse;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GuideController — thin adapter: resolves the current user and delegates to GuideService /
 * GuideEarningsService, wrapping results in the {@code {data, meta}} envelope. These tests verify
 * the delegation (the business rules themselves live in, and are tested via, the service classes).
 */
@ExtendWith(MockitoExtension.class)
class GuideControllerTest {

    @Mock CurrentUser currentUser;
    @Mock GuideService guideService;
    @Mock GuideEarningsService guideEarningsService;

    private GuideController controller() {
        return new GuideController(currentUser, guideService, guideEarningsService);
    }

    private static GuideProfileResponse profileResponse() {
        return new GuideProfileResponse(
                "uid", null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private static GuideDashboardStatsResponse statsResponse() {
        return new GuideDashboardStatsResponse(null, 0, 0L, 0L, "USD");
    }

    @Test
    void getProfile_delegatesAndWrapsInEnvelope() {
        UserEntity u = new UserEntity();
        GuideProfileResponse resp = profileResponse();
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
        GuideProfileResponse resp = profileResponse();
        when(currentUser.require()).thenReturn(u);
        when(guideService.updateProfile(u, req)).thenReturn(resp);

        assertSame(resp, controller().updateProfile(req).data());
    }

    @Test
    void getDashboardStats_delegatesAndWrapsInEnvelope() {
        UserEntity u = new UserEntity();
        GuideDashboardStatsResponse stats = statsResponse();
        when(currentUser.require()).thenReturn(u);
        when(guideEarningsService.getStats(u)).thenReturn(stats);

        assertSame(stats, controller().getDashboardStats().data());
    }
}
