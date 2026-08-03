package com.CampusToursLive.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Best-effort degradation paths of {@link IdempotencyFilter} that a real database cannot produce
 * deterministically (a transient reserve failure, and the reservation vanishing between the failed
 * INSERT and the re-read). Driven with a mocked {@link JdbcTemplate} so each fault is reachable.
 * The invariant under test: dedupe bookkeeping never fails an otherwise-valid write — the handler
 * still runs, and nothing is recorded.
 */
class IdempotencyFilterDegradeTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final IdempotencyFilter filter = new IdempotencyFilter(jdbc);

    private MockHttpServletRequest post(String key) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/bookings");
        req.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        req.addHeader("Idempotency-Key", key);
        return req;
    }

    private FilterChain handlerReturning(int status, int[] calls) {
        return (req, resp) -> {
            calls[0]++;
            ((HttpServletResponse) resp).setStatus(status);
        };
    }

    @Test
    void reserveTransientFailure_runsHandlerUnrecorded() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        int[] calls = {0};
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(post("k-1"), resp, handlerReturning(201, calls));

        assertThat(calls[0]).isEqualTo(1); // write still ran
        assertThat(resp.getStatus()).isEqualTo(201);
        verify(jdbc, never()).queryForMap(anyString(), any(Object[].class)); // never reached replay
    }

    @Test
    void reservationReapedBeforeReRead_runsHandlerUnrecorded() throws Exception {
        // INSERT trips the unique index (a duplicate exists)…
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("dup"));
        // …but by the time we re-read, the row is gone (original failed/deleted, or TTL swept).
        when(jdbc.queryForMap(anyString(), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        int[] calls = {0};
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(post("k-2"), resp, handlerReturning(201, calls));

        assertThat(calls[0]).isEqualTo(1); // fell through to running the handler, not a 500
        assertThat(resp.getStatus()).isEqualTo(201);
    }
}
