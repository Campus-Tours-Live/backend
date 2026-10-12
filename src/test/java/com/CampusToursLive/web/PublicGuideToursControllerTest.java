package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * PublicGuideToursController — thin adapter: delegates to TourDiscoveryService.listByGuide and
 * wraps the page in the {@code {data, meta}} envelope. No auth (the route is permitAll).
 */
@ExtendWith(MockitoExtension.class)
class PublicGuideToursControllerTest {

    @Mock TourDiscoveryService discovery;

    private PublicGuideToursController controller() {
        return new PublicGuideToursController(discovery);
    }

    @Test
    void tours_delegates_andWrapsPageInEnvelope() {
        UUID guideId = UUID.randomUUID();
        Page<TourSummaryResponse> page = new PageImpl<>(List.of());
        when(discovery.listByGuide(eq(guideId), eq(2), eq(10))).thenReturn(page);

        var env = controller().tours(guideId, 2, 10);
        assertEquals(page.getContent(), env.data().items());
        verify(discovery).listByGuide(guideId, 2, 10);
    }
}
