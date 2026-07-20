package com.CampusToursLive.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Idempotency filter against a REAL PostgreSQL (Testcontainers) — exercises the reserve/replay,
 * in-flight {@code 409}, misuse {@code 422}, and failure-cleanup paths that only a real {@code
 * UNIQUE(operation, idempotency_key)} + {@code jsonb} column can prove. The filter is driven
 * directly (a {@link FilterChain} lambda stands in for the handler) so each path is reachable
 * deterministically without the full web/security stack.
 *
 * <p><b>Non-transactional on purpose.</b> This overrides {@code @DataJpaTest}'s default
 * test-managed transaction ({@code NOT_SUPPORTED}) so the filter's {@code JdbcTemplate} runs in
 * autocommit — exactly like production, where the filter executes outside any
 * {@code @Transactional}. That is essential: the reserve is a unique-violation-driven {@code
 * INSERT}, and in Postgres a failed statement aborts the enclosing transaction — under a single
 * shared test transaction the follow-up {@code SELECT} (the 409/422/replay branch) would fail with
 * "transaction is aborted". Autocommit makes each statement its own transaction, which is the real
 * code path. Rows therefore persist between tests, so {@link #setUp()} clears the table before
 * each.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdempotencyFilterIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired DataSource dataSource;

    private JdbcTemplate jdbc;
    private IdempotencyFilter filter;
    private int handlerCalls;
    private String bodySeenByHandler;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        filter = new IdempotencyFilter(jdbc);
        handlerCalls = 0;
        bodySeenByHandler = null;
        // Rows persist across tests (autocommit, no rollback) — start each test from empty.
        jdbc.update("DELETE FROM idempotency_keys");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private MockHttpServletRequest post(String path, String body, String key) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (key != null) {
            req.addHeader("Idempotency-Key", key);
        }
        return req;
    }

    /**
     * A handler that reads the body (via getInputStream) and writes {@code status} + {@code body}.
     */
    private FilterChain handler(int status, String body) {
        return (req, resp) -> {
            handlerCalls++;
            bodySeenByHandler =
                    new String(
                            StreamUtils.copyToByteArray(req.getInputStream()),
                            StandardCharsets.UTF_8);
            HttpServletResponse http = (HttpServletResponse) resp;
            http.setStatus(status);
            http.setContentType("application/json");
            if (body != null) {
                http.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private int rowsFor(String key) {
        Integer n =
                jdbc.queryForObject(
                        "SELECT count(*) FROM idempotency_keys WHERE idempotency_key = ?",
                        Integer.class,
                        key);
        return n == null ? 0 : n;
    }

    // ── tests ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void sameKeyTwiceSequentially_onlyOneRow_secondReplaysStoredResponse() throws Exception {
        String key = "k-" + UUID.randomUUID();
        String body = "{\"offeringId\":\"o1\"}";

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(post("/bookings", body, key), first, handler(201, "{\"id\":\"b1\"}"));

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(first.getContentAsString()).isEqualTo("{\"id\":\"b1\"}");
        assertThat(bodySeenByHandler).isEqualTo(body); // cached body reached the handler intact
        assertThat(rowsFor(key)).isEqualTo(1);
        assertThat(handlerCalls).isEqualTo(1);

        // Second, identical request: handler must NOT run again; the stored response is replayed.
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(post("/bookings", body, key), second, handler(201, "SHOULD-NOT-RUN"));

        assertThat(second.getStatus()).isEqualTo(201);
        // Replay reads the stored `jsonb`, which Postgres re-serializes (whitespace after the
        // colon)
        // — semantically identical to the original, which is all idempotent replay requires.
        assertThat(second.getContentAsString().replace(" ", "")).isEqualTo("{\"id\":\"b1\"}");
        assertThat(rowsFor(key)).isEqualTo(1);
        assertThat(handlerCalls).isEqualTo(1); // unchanged — the replay did not invoke the handler
    }

    @Test
    void differentKeys_produceTwoRows() throws Exception {
        String keyA = "k-" + UUID.randomUUID();
        String keyB = "k-" + UUID.randomUUID();
        String body = "{\"offeringId\":\"o1\"}";

        filter.doFilter(
                post("/bookings", body, keyA),
                new MockHttpServletResponse(),
                handler(201, "{\"id\":\"a\"}"));
        filter.doFilter(
                post("/bookings", body, keyB),
                new MockHttpServletResponse(),
                handler(201, "{\"id\":\"b\"}"));

        assertThat(rowsFor(keyA)).isEqualTo(1);
        assertThat(rowsFor(keyB)).isEqualTo(1);
        assertThat(handlerCalls).isEqualTo(2);
    }

    @Test
    void inFlightDuplicate_returns409() throws Exception {
        String key = "k-" + UUID.randomUUID();
        // Pre-seed a reserved-but-not-completed row (response_status NULL) — the double-submit case
        // a sequential two-shot test can't otherwise reach.
        jdbc.update(
                "INSERT INTO idempotency_keys (idempotency_key, operation, request_hash, expires_at)"
                        + " VALUES (?, ?, ?, ?)",
                key,
                "POST /bookings",
                "any-hash",
                Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(post("/bookings", "{\"offeringId\":\"o1\"}", key), resp, handler(201, "x"));

        assertThat(resp.getStatus()).isEqualTo(409);
        assertThat(resp.getContentType()).isEqualTo("application/problem+json");
        assertThat(resp.getContentAsString()).contains("Request in progress");
        assertThat(handlerCalls).isEqualTo(0); // handler never ran behind the in-flight request
    }

    @Test
    void sameKeyDifferentBody_returns422() throws Exception {
        String key = "k-" + UUID.randomUUID();

        filter.doFilter(
                post("/bookings", "{\"offeringId\":\"o1\"}", key),
                new MockHttpServletResponse(),
                handler(201, "{\"id\":\"b1\"}"));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(
                post("/bookings", "{\"offeringId\":\"DIFFERENT\"}", key),
                resp,
                handler(201, "{\"id\":\"b2\"}"));

        assertThat(resp.getStatus()).isEqualTo(422);
        assertThat(resp.getContentType()).isEqualTo("application/problem+json");
        assertThat(resp.getContentAsString()).contains("different request");
        assertThat(handlerCalls).isEqualTo(1); // only the first (real) request ran
    }

    @Test
    void handlerReturns500_reservationDeleted_andSameKeyRetryAllowed() throws Exception {
        String key = "k-" + UUID.randomUUID();
        String body = "{\"offeringId\":\"o1\"}";

        MockHttpServletResponse failed = new MockHttpServletResponse();
        filter.doFilter(post("/bookings", body, key), failed, handler(500, "{\"error\":\"boom\"}"));

        assertThat(failed.getStatus()).isEqualTo(500);
        assertThat(rowsFor(key)).isEqualTo(0); // reservation dropped — not left as a 409 landmine

        // A legitimate retry of the same key now succeeds (not wedged on 409 in-flight).
        MockHttpServletResponse retry = new MockHttpServletResponse();
        filter.doFilter(post("/bookings", body, key), retry, handler(201, "{\"id\":\"b1\"}"));

        assertThat(retry.getStatus()).isEqualTo(201);
        assertThat(retry.getContentAsString()).isEqualTo("{\"id\":\"b1\"}");
        assertThat(rowsFor(key)).isEqualTo(1);
        assertThat(handlerCalls).isEqualTo(2); // both attempts invoked the handler
    }

    @Test
    void handlerThrows_reservationDeleted_andExceptionPropagates() {
        String key = "k-" + UUID.randomUUID();
        FilterChain boom =
                (req, resp) -> {
                    handlerCalls++;
                    throw new IllegalStateException("kaboom");
                };

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        post("/bookings", "{\"a\":1}", key),
                                        new MockHttpServletResponse(),
                                        boom))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("kaboom");
        assertThat(rowsFor(key)).isEqualTo(0);
    }

    @Test
    void nonJsonResponseBody_persistsStatusWithoutBody_andReplaysEmptyBody() throws Exception {
        String key = "k-" + UUID.randomUUID();
        String body = "{\"offeringId\":\"o1\"}";

        MockHttpServletResponse first = new MockHttpServletResponse();
        // Plain-text (non-JSON) body — the jsonb cast rejects it, so only the status is persisted.
        filter.doFilter(post("/bookings", body, key), first, handler(200, "plain text, not json"));
        assertThat(first.getStatus()).isEqualTo(200);

        Integer storedStatus =
                jdbc.queryForObject(
                        "SELECT response_status FROM idempotency_keys WHERE idempotency_key = ?",
                        Integer.class,
                        key);
        String storedBody =
                jdbc.queryForObject(
                        "SELECT response_body FROM idempotency_keys WHERE idempotency_key = ?",
                        String.class,
                        key);
        assertThat(storedStatus).isEqualTo(200);
        assertThat(storedBody).isNull();

        // Replay: status echoed, body empty (nothing to echo).
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(post("/bookings", body, key), second, handler(200, "SHOULD-NOT-RUN"));
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(second.getContentAsString()).isEmpty();
        assertThat(handlerCalls).isEqualTo(1);
    }

    @Test
    void emptyResponseBody_persistsStatusOnly_andReplaysEmptyBody() throws Exception {
        String key = "k-" + UUID.randomUUID();
        String body = "{\"a\":1}";

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(post("/bookings", body, key), first, handler(204, null)); // no body written
        assertThat(first.getStatus()).isEqualTo(204);

        String storedBody =
                jdbc.queryForObject(
                        "SELECT response_body FROM idempotency_keys WHERE idempotency_key = ?",
                        String.class,
                        key);
        assertThat(storedBody).isNull();

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(post("/bookings", body, key), second, handler(204, "SHOULD-NOT-RUN"));
        assertThat(second.getStatus()).isEqualTo(204);
        assertThat(second.getContentAsString()).isEmpty();
        assertThat(handlerCalls).isEqualTo(1);
    }

    @Test
    void noIdempotencyKey_passesThroughWithoutReserving() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(post("/bookings", "{\"a\":1}", null), resp, handler(201, "{\"id\":\"x\"}"));

        assertThat(resp.getStatus()).isEqualTo(201);
        assertThat(handlerCalls).isEqualTo(1);
        Integer total = jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Integer.class);
        assertThat(total).isZero();
    }

    @Test
    void blankIdempotencyKey_passesThroughWithoutReserving() throws Exception {
        MockHttpServletRequest req = post("/bookings", "{\"a\":1}", "   ");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, handler(201, "{\"id\":\"x\"}"));

        assertThat(resp.getStatus()).isEqualTo(201);
        assertThat(handlerCalls).isEqualTo(1);
        Integer total = jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Integer.class);
        assertThat(total).isZero();
    }

    @Test
    void cachedBodyWrapper_replaysBodyViaStreamAndReader() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/bookings");
        CachedBodyHttpServletRequest wrapper =
                new CachedBodyHttpServletRequest(raw, "hello".getBytes(StandardCharsets.UTF_8));

        var stream = wrapper.getInputStream();
        assertThat(stream.isReady()).isTrue();
        assertThat(stream.isFinished()).isFalse();
        assertThat(stream.read()).isEqualTo((int) 'h');
        assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("ello");
        assertThat(stream.isFinished()).isTrue();
        assertThatThrownBy(() -> stream.setReadListener(null))
                .isInstanceOf(UnsupportedOperationException.class);

        // A fresh read via getReader() replays the same captured body.
        assertThat(wrapper.getReader().readLine()).isEqualTo("hello");
    }

    @Test
    void nonMutatingMethodWithKey_passesThroughWithoutReserving() throws Exception {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/tours");
        get.addHeader("Idempotency-Key", "k-" + UUID.randomUUID());
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(get, resp, handler(200, "[]"));

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(handlerCalls).isEqualTo(1);
        Integer total = jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Integer.class);
        assertThat(total).isZero();
    }

    @Test
    void purgeExpired_deletesOnlyExpiredRows() {
        String expired = "k-" + UUID.randomUUID();
        String live = "k-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO idempotency_keys (idempotency_key, operation, request_hash, expires_at)"
                        + " VALUES (?, ?, ?, ?)",
                expired,
                "POST /bookings",
                "h",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        jdbc.update(
                "INSERT INTO idempotency_keys (idempotency_key, operation, request_hash, expires_at)"
                        + " VALUES (?, ?, ?, ?)",
                live,
                "POST /bookings",
                "h",
                Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));

        filter.purgeExpired();

        assertThat(rowsFor(expired)).isZero();
        assertThat(rowsFor(live)).isEqualTo(1);
    }
}
