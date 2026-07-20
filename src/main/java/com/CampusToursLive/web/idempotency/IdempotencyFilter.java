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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 *   <li><b>Reserve.</b> Drain the body, compute {@code request_hash = sha256(method + path +
 *       body)}, and {@code INSERT} a row with {@code response_status = NULL} into {@code
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

        String path = request.getRequestURI();
        String operation = request.getMethod() + " " + path;
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        String requestHash = sha256(request.getMethod(), path, body);

        try {
            jdbc.update(
                    INSERT_SQL,
                    key,
                    operation,
                    requestHash,
                    Timestamp.from(Instant.now().plus(TTL)));
        } catch (DuplicateKeyException duplicate) {
            replayOrReject(response, operation, key, requestHash);
            return;
        }

        runHandlerAndRecord(request, response, filterChain, operation, key, body);
    }

    /** A key we've seen before: in-flight 409, misuse 422, or replay the stored response. */
    private void replayOrReject(
            HttpServletResponse response, String operation, String key, String requestHash)
            throws IOException {
        Map<String, Object> row = jdbc.queryForMap(SELECT_SQL, operation, key);
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
        response.setContentType("application/json");
        if (storedBody != null) {
            response.getOutputStream()
                    .write(storedBody.toString().getBytes(StandardCharsets.UTF_8));
        }
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
        } catch (DataAccessException notJson) {
            // Non-JSON body (e.g. an empty 204 or a plain-text response) — the jsonb cast rejected
            // it; persist the status so retries still replay, just without a body to echo.
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

    private static String sha256(String method, String path, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update(path.getBytes(StandardCharsets.UTF_8));
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
