package com.CampusToursLive.domain.booking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class BookingCompletionJobTest {

    private final BookingCompletionService completion = mock(BookingCompletionService.class);
    private final BookingCompletionJob job = new BookingCompletionJob(completion);

    @Test
    void sweep_keepsTakingFullBatches_untilAShortOne() {
        int full = BookingCompletionJob.BATCH_SIZE;
        when(completion.completeEndedBatch(full)).thenReturn(full, full, 7);

        job.sweep();

        verify(completion, times(3)).completeEndedBatch(full);
    }

    @Test
    void sweep_isCappedPerTick_soABacklogCannotMonopolizeTheScheduler() {
        when(completion.completeEndedBatch(anyInt())).thenReturn(BookingCompletionJob.BATCH_SIZE);

        job.sweep();

        verify(completion, times(BookingCompletionJob.MAX_BATCHES_PER_TICK))
                .completeEndedBatch(BookingCompletionJob.BATCH_SIZE);
    }

    @Test
    void sweep_withNothingDue_runsOneBatch() {
        when(completion.completeEndedBatch(anyInt())).thenReturn(0);

        job.sweep();

        verify(completion).completeEndedBatch(BookingCompletionJob.BATCH_SIZE);
    }

    @Test
    void sweep_neverLetsAFailureEscapeTheSchedulerThread() {
        when(completion.completeEndedBatch(anyInt()))
                .thenReturn(BookingCompletionJob.BATCH_SIZE)
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(job::sweep).doesNotThrowAnyException();
    }
}
