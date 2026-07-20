package com.CampusToursLive.web.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Server-side idempotency for mutating requests carrying an {@code Idempotency-Key} header.
 *
 * <p>Flow (only for POST/PATCH/PUT/DELETE with a non-blank {@code Idempotency-Key}; every other
 * request passes straight through, so dedupe is strictly best-effort and opt-in):
 *
 * <ol>
 *   <li><b>Reserve.</b> Drain the body, compute {@code request_hash = sha256(method + path + query
 *       + body)}, and {@code INSERT} a row with {@code response_status = NULL} into {@code
 *       idempotency_keys}, relying on {@code UNIQUE(operation, idempotency_key)} to detect
 *       duplicates.
 *   <li><b>Duplicate branch</b> (unique violation): re-read the existing row and either return
 *       {@code 409} (reserved but not yet completed — an in-flight double-submit), {@code 422}
 *       (same key, <em>different</em> request hash — client misuse), or <b>replay</b> the stored
 *       status + body (a completed retry).
 *   <li><b>Run the handler,</b> capture its status + body via {@link
 *       ContentCachingResponseWrapper}.
 *   <li><b>Finalize.</b> On success (2xx, or a 4xx business response) {@code UPDATE} the reserved
 *       row with the captured status/body so future retries replay it. On failure (status {@code >=
 *       500} or a thrown exception) {@code DELETE} the reservation so a legitimate retry is not
 *       wedged on the {@code 409} in-flight branch until the TTL expires.
 * </ol>
 *
 * <p><b>Per-caller scope.</b> The reserve folds the authenticated subject into {@code operation}
 * (the server-derived half of {@code UNIQUE(operation, idempotency_key)}), so two
 * <em>different</em> users that happen to reuse the same client key never collide — without that,
 * one caller's key could 422 the other's write or, on a matching hash, replay the first caller's
 * response to the second. This filter runs nested inside (ordered after) the Spring Security chain,
 * so the {@link SecurityContextHolder} is still populated here; unauthenticated mutations (none
 * today — every write is behind auth) fall back to a fixed {@code "anon"} bucket.
 *
 * <p><b>Best-effort degradation.</b> Because dedupe is opt-in bookkeeping, it must never fail an
 * otherwise-valid write. If the reserve {@code INSERT} fails for any reason <em>other</em> than a
 * duplicate (a transient DB blip), or the duplicate-branch re-read finds the row already reaped
 * (the original failed and deleted it, or the TTL sweep removed it), the request runs unrecorded
 * rather than {@code 500}-ing. The natural-key constraints below remain the real duplicate-write
 * defense in those windows.
 *
 * <p><b>Replay fidelity (accepted limits).</b> Replay echoes the stored status + body only. The
 * body is stored as {@code jsonb}, so Postgres re-serializes it (key order / whitespace may differ)
 * — semantically identical, which is all idempotent replay requires; there are no byte-sensitive
 * (ETag/signature) consumers of these responses. The media type is fixed at {@code
 * application/json} and no other response headers are restored, because this API's mutations return
 * JSON envelopes in the body and set no {@code Location} or custom headers; the table stores no
 * header metadata by design.
 *
 * <p><b>Transaction semantics — do not "fix" this.</b> The reserve {@code INSERT} commits in its
 * own transaction (this filter runs outside any {@code @Transactional}, so the {@link JdbcTemplate}
 * autocommits) <em>before</em> the handler runs. That separate commit is exactly what makes the
 * in-flight {@code 409} reachable: if the reserve, the handler, and the final {@code UPDATE} shared
 * one transaction, a concurrent duplicate would block on the unique index until the first request's
 * whole transaction ended and would never reach the {@code 409} branch — it would just hang behind
 * the first request. The documented, intentional trade-off is that the reservation is <em>not</em>
 * atomic with the business write: a crash between the handler's commit and the finalizing {@code
 * UPDATE} can orphan a reservation next to a completed side-effect. That is acceptable here because
 * (a) the highest-risk writes have natural-key constraints (create-booking → {@code
 * excl_participant_no_overlap}; create-offering → {@code uq_tour_guide_slug}) that reject the real
 * duplicate write even if this header path is bypassed, and (b) {@link #purgeExpired()} reaps the
 * orphan after the TTL. Folding the reservation into the write transaction to make it "atomic"
 * would re-break the {@code 409} path.
 *
 * <p>Registered (and ordered after Spring Security) via a {@code FilterRegistrationBean} in {@code
 * WebConfig} rather than component-scanned, so it is not pulled into {@code @WebMvcTest} slices
 * that have no {@link JdbcTemplate}.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    static final String HEADER = "Idempotency-Key";
    static final Duration TTL = Duration.ofHours(24);
    private static final Set<String> MUTATING = Set.of("POST", "PATCH", "PUT", "DELETE");

    private static final String INSERT_SQL =
            "INSERT INTO idempotency_keys (idempotency_key, operation, request_hash, expires_at)"
                    + " VALUES (?, ?, ?, ?)";
    private static final String SELECT_SQL =
            "SELECT response_status, response_body, request_hash FROM idempotency_keys"
                    + " WHERE operation = ? AND idempotency_key = ?";
    private static final String UPDATE_SQL =
            "UPDATE idempotency_keys SET response_status = ?, response_body = CAST(? AS jsonb)"
                    + " WHERE operation = ? AND idempotency_key = ?";
    private static final String DELETE_SQL =
            "DELETE FROM idempotency_keys WHERE operation = ? AND idempotency_key = ?";

    private final JdbcTemplate jdbc;

    public IdempotencyFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || !MUTATING.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Include the query string so two requests that differ only in their query (same key +
        // body) are treated as distinct operations, not silently deduped into a replay of the
        // first.
        String query = request.getQueryString();
        String target = request.getRequestURI() + (query == null ? "" : "?" + query);
        String operation = subject() + " " + request.getMethod() + " " + target;
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        String requestHash = sha256(request.getMethod(), target, body);

        try {
            jdbc.update(
                    INSERT_SQL,
                    key,
                    operation,
                    requestHash,
                    Timestamp.from(Instant.now().plus(TTL)));
        } catch (DuplicateKeyException duplicate) {
            replayOrReject(request, response, filterChain, operation, key, body, requestHash);
            return;
        } catch (DataAccessException unavailable) {
            // Not a duplicate — a transient DB failure on the bookkeeping INSERT. Dedupe is
            // best-effort, so degrade to running the write unrecorded rather than failing it.
            log.warn("Idempotency reserve failed; proceeding without dedupe", unavailable);
            proceedUnrecorded(request, response, filterChain, body);
            return;
        }

        runHandlerAndRecord(request, response, filterChain, operation, key, body);
    }

    /** A key we've seen before: in-flight 409, misuse 422, or replay the stored response. */
    private void replayOrReject(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            String operation,
            String key,
            byte[] body,
            String requestHash)
            throws IOException, ServletException {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(SELECT_SQL, operation, key);
        } catch (EmptyResultDataAccessException reaped) {
            // The reservation vanished between our failed INSERT and this read — the original
            // request failed (reservation deleted) or the TTL sweep purged it. Treat as unseen and
            // run the handler, rather than 500-ing a legitimate retry on a missing row.
            proceedUnrecorded(request, response, filterChain, body);
            return;
        }
        Integer status = (Integer) row.get("response_status");
        if (status == null) {
            writeProblem(
                    response,
                    409,
                    "Request in progress",
                    "A request with this Idempotency-Key is still being processed; retry shortly.");
            return;
        }
        String storedHash = (String) row.get("request_hash");
        if (!requestHash.equals(storedHash)) {
            writeProblem(
                    response,
                    422,
                    "Idempotency-Key reused with a different request",
                    "This Idempotency-Key was already used for a different request body.");
            return;
        }
        Object storedBody = row.get("response_body");
        response.setStatus(status);
        // Fixed media type + no header restoration — accepted limitation (see class doc).
        response.setContentType("application/json");
        if (storedBody != null) {
            response.getOutputStream()
                    .write(storedBody.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Run the handler with the drained body but keep no idempotency record (degraded path). */
    private void proceedUnrecorded(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            byte[] body)
            throws IOException, ServletException {
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    /** Run the handler with a replayable body, then persist or drop the reservation. */
    private void runHandlerAndRecord(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            String operation,
            String key,
            byte[] body)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedRequest =
                new CachedBodyHttpServletRequest(request, body);
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cachedRequest, cachingResponse);
        } catch (ServletException | IOException | RuntimeException ex) {
            // The handler blew up before producing a response — drop the reservation so the
            // legitimate retry isn't stuck on the 409 in-flight branch until the TTL.
            deleteReservation(operation, key);
            throw ex;
        }

        int status = cachingResponse.getStatus();
        if (status >= 500) {
            deleteReservation(operation, key);
        } else {
            storeResponse(operation, key, status, cachingResponse.getContentAsByteArray());
        }
        cachingResponse.copyBodyToResponse();
    }

    private void storeResponse(String operation, String key, int status, byte[] body) {
        String json =
                (body == null || body.length == 0)
                        ? null
                        : new String(body, StandardCharsets.UTF_8);
        try {
            jdbc.update(UPDATE_SQL, status, json, operation, key);
        } catch (DataIntegrityViolationException notJson) {
            // The captured body isn't valid JSON (e.g. a plain-text or empty response) — the jsonb
            // CAST rejected it (SQLSTATE class 22). Persist the status alone so retries still
            // replay. Narrow on purpose: a transient/connection DataAccessException must propagate,
            // not be swallowed into a body-less row.
            jdbc.update(UPDATE_SQL, status, null, operation, key);
        }
    }

    private void deleteReservation(String operation, String key) {
        jdbc.update(DELETE_SQL, operation, key);
    }

    /**
     * Backstop sweep: delete rows past their TTL so orphaned/expired reservations don't accumulate
     * (the {@code ix_idem_expires} index keeps this cheap). Hourly.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3_600_000L)
    public void purgeExpired() {
        jdbc.update("DELETE FROM idempotency_keys WHERE expires_at < now()");
    }

    /** The authenticated caller's subject, or {@code "anon"} when there is no authentication. */
    private static String subject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anon";
    }

    private static String sha256(String method, String target, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update(target.getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JCA algorithm; this cannot happen on a conformant JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void writeProblem(
            HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        String json =
                "{\"title\":\""
                        + escape(title)
                        + "\",\"status\":"
                        + status
                        + ",\"detail\":\""
                        + escape(detail)
                        + "\"}";
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
