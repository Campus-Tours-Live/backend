package com.CampusToursLive.domain.reschedule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class RescheduleExpiryJobTest {

    private final RescheduleService service = mock(RescheduleService.class);
    private final RescheduleExpiryJob job = new RescheduleExpiryJob(service);

    @Test
    void sweep_delegatesToTheService() {
        when(service.expirePastDueProposals()).thenReturn(2);

        job.sweep();

        verify(service).expirePastDueProposals();
    }

    @Test
    void sweep_withNothingToExpire_isQuiet() {
        when(service.expirePastDueProposals()).thenReturn(0);

        assertThatCode(job::sweep).doesNotThrowAnyException();
        verify(service).expirePastDueProposals();
    }

    @Test
    void sweep_neverLetsAFailureEscapeTheSchedulerThread() {
        when(service.expirePastDueProposals()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(job::sweep).doesNotThrowAnyException();
    }
}
