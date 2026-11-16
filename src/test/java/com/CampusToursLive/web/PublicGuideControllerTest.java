package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.web.dto.PublicGuideProfileResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PublicGuideController — thin adapter: delegates to GuideService and wraps the result in the
 * {@code {data, meta}} envelope. No auth (the route is permitAll); logic lives in the service.
 */
@ExtendWith(MockitoExtension.class)
class PublicGuideControllerTest {

    @Mock GuideService guideService;

    private PublicGuideController controller() {
        return new PublicGuideController(guideService);
    }

    @Test
    void get_delegates_andWrapsInEnvelope() {
        UUID guideId = UUID.randomUUID();
        PublicGuideProfileResponse profile =
                new PublicGuideProfileResponse(
                        guideId.toString(), "Maya Chen", "bio", List.of("en-US"), List.of("STEM"));
        when(guideService.getPublicProfile(guideId)).thenReturn(profile);

        assertSame(profile, controller().get(guideId).data());
        verify(guideService).getPublicProfile(guideId);
    }
}
