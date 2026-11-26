package com.CampusToursLive.domain.booking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class BookingCompletionServiceTest {

    @Test
    void negativeGrace_isRejectedAtStartup() {
        assertThatThrownBy(
                        () ->
                                new BookingCompletionService(
                                        mock(BookingRepository.class),
                                        mock(BookingStatusHistoryRepository.class),
                                        Clock.systemUTC(),
                                        -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grace-min");
    }
}
