package com.CampusToursLive.domain.availability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GuideBookingSettingsServiceTest {

    @Mock GuideBookingSettingsRepository bookingSettings;

    private GuideBookingSettingsService service() {
        return new GuideBookingSettingsService(bookingSettings);
    }

    @Test
    void getOrCreate_returnsExistingWithoutInsert() {
        UUID guideId = UUID.randomUUID();
        GuideBookingSettingsEntity existing = new GuideBookingSettingsEntity();
        existing.setGuideId(guideId);
        when(bookingSettings.findById(guideId)).thenReturn(Optional.of(existing));

        GuideBookingSettingsEntity result = service().getOrCreate(guideId);
        assertEquals(guideId, result.getGuideId());
        verify(bookingSettings, never()).save(any());
    }

    @Test
    void getOrCreate_insertsDefaultsWhenMissing() {
        UUID guideId = UUID.randomUUID();
        when(bookingSettings.findById(guideId)).thenReturn(Optional.empty());
        when(bookingSettings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GuideBookingSettingsEntity result = service().getOrCreate(guideId);
        assertEquals(guideId, result.getGuideId());
        verify(bookingSettings).save(any());
    }

    @Test
    void getOrCreate_reloadsAfterConcurrentInsert() {
        UUID guideId = UUID.randomUUID();
        GuideBookingSettingsEntity existing = new GuideBookingSettingsEntity();
        existing.setGuideId(guideId);

        when(bookingSettings.findById(guideId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(bookingSettings.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        GuideBookingSettingsEntity result = service().getOrCreate(guideId);
        assertEquals(guideId, result.getGuideId());
    }
}
