package com.CampusToursLive.integration.scorecard;

import com.CampusToursLive.integration.scorecard.SchoolDirectory.SchoolRef;
import com.CampusToursLive.web.MetaController.Option;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * The <strong>outbound</strong> half of the College Scorecard integration: it owns the {@link
 * RestClient} + the shared server-side API key, performs the HTTP calls, and parses the responses.
 *
 * <p><strong>Why this is a separate bean from {@link ScorecardClient} — do not merge them.</strong>
 * Both {@code @Cacheable} (Layer 1, on {@code ScorecardClient}) and {@code @RateLimiter} (Layer 2,
 * here) are proxy-based Spring AOP. A self-invocation — {@code this.callScorecard(...)} from inside
 * the same bean — does <em>not</em> go through the proxy, so a {@code @RateLimiter} on a private or
 * inner method of {@code ScorecardClient} would silently never apply. Splitting the two annotations
 * across two beans, with a real injected-dependency call between them, is what makes both aspects
 * fire. It also gives us the property that actually matters for quota:
 *
 * <p><strong>a cache HIT never consumes a rate-limit permit</strong> — on a hit, Spring's cache
 * interceptor returns before {@code ScorecardClient} ever calls into this bean, so the limiter is
 * never entered. Collapsing these classes back into one would break that guarantee.
 *
 * <p>The two layers defend against different threats: the cache absorbs <em>organic repetition</em>
 * (everyone searches the same popular schools), while the rate limiter is the real gate against
 * <em>malicious random queries</em>, which are 100% cache-miss by construction.
 *
 * <p>Every method degrades rather than throws: a blank API key, a blank argument, an upstream
 * failure, or an exhausted rate limiter all yield the same empty/null value the callers already
 * handle.
 */
@Component
public class ScorecardApi {

    private static final Logger log = LoggerFactory.getLogger(ScorecardApi.class);

    private final RestClient http;
    private final String apiKey;

    @Autowired
    public ScorecardApi(
            @Value("${app.scorecard.base-url:https://api.data.gov/ed/collegescorecard/v1}")
                    String baseUrl,
            @Value("${app.scorecard.api-key:}") String apiKey) {
        this(defaultClient(baseUrl), apiKey);
    }

    /** Test seam: inject a pre-built {@link RestClient} (e.g. bound to MockRestServiceServer). */
    ScorecardApi(RestClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    private static RestClient defaultClient(String baseUrl) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5000);
        rf.setReadTimeout(15000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
    }

    /**
     * <strong>THE DIRECTORY BOUNDARY — what this platform means by "university".</strong>
     *
     * <p>Unfiltered, Scorecard lists <strong>6,273</strong> schools: every cosmetology school,
     * truck-driving academy and certificate programme in the country. A student browsing campus
     * tours does not mean any of those, and a guide typing "academy" should not be offered them.
     * With this filter the directory is <strong>1,944</strong> institutions — 1,903 across the 50
     * states and DC, the remaining 41 in the territories.
     *
     * <ul>
     *   <li>{@code school.operating=1} — still open. Closed schools cannot host a tour.
     *   <li>{@code school.degrees_awarded.predominant=3} — <em>predominantly</em> bachelor's. Not a
     *       floor but a centre of mass: a school whose main output is associate degrees is a
     *       community college, and one whose main output is graduate degrees is a professional
     *       school. Widening to {@code 3..4} adds 263 mostly-graduate institutions; that is a
     *       product decision, and it belongs here, changed once, rather than in a caller.
     * </ul>
     *
     * <p><strong>Where it does and does not apply.</strong> It gates what a guide may CHOOSE
     * ({@link #searchSchools}) and what the browsable directory lists ({@link #directoryPage}), so
     * the onboarding picker and the browse-by-state pages can never describe different populations.
     * It deliberately does NOT gate {@link #getSchool}: that resolves an id a guide already saved,
     * and filtering it would turn every existing out-of-boundary affiliation into {@code Unknown
     * university} the moment this shipped. The boundary governs what can be picked, not what can be
     * displayed.
     */
    private static UriBuilder withinDirectory(UriBuilder uri) {
        return uri.queryParam("school.operating", 1)
                .queryParam("school.degrees_awarded.predominant", 3);
    }

    /** Search schools by (partial) name. Empty list on blank key/query or any failure. */
    @RateLimiter(name = "scorecard", fallbackMethod = "searchSchoolsRateLimited")
    public List<Option> searchSchools(String query, int limit) {
        if (apiKey.isBlank() || query == null || query.isBlank()) return List.of();
        int perPage = Math.min(Math.max(limit, 1), 50);
        try {
            JsonNode root =
                    http.get()
                            .uri(
                                    uri ->
                                            withinDirectory(uri.path("/schools"))
                                                    .queryParam("school.name", query.trim())
                                                    .queryParam(
                                                            "fields",
                                                            "id,school.name,school.city,school.state")
                                                    .queryParam("per_page", perPage)
                                                    .queryParam("api_key", apiKey)
                                                    .build())
                            .retrieve()
                            .body(JsonNode.class);
            List<Option> out = new ArrayList<>();
            for (JsonNode r : root.path("results")) {
                String id = r.path("id").asText("");
                String name = r.path("school.name").asText("");
                if (id.isBlank() || name.isBlank()) continue;
                String city = r.path("school.city").asText("");
                String state = r.path("school.state").asText("");
                String loc =
                        city.isBlank() && state.isBlank()
                                ? ""
                                : " — " + city + (state.isBlank() ? "" : ", " + state);
                out.add(new Option(id, name + loc));
            }
            return out;
        } catch (Exception ex) {
            log.warn("Scorecard school search failed for '{}': {}", query, ex.toString());
            return List.of();
        }
    }

    /** The distinct majors (CIP-4 titles) a school offers. Empty list on blank/failure. */
    @RateLimiter(name = "scorecard", fallbackMethod = "majorsForSchoolRateLimited")
    public List<Option> majorsForSchool(String schoolId) {
        if (apiKey.isBlank() || schoolId == null || schoolId.isBlank()) return List.of();
        try {
            JsonNode root =
                    http.get()
                            .uri(
                                    uri ->
                                            uri.path("/schools")
                                                    .queryParam("id", schoolId.trim())
                                                    .queryParam(
                                                            "fields",
                                                            "latest.programs.cip_4_digit.title")
                                                    .queryParam("per_page", 1)
                                                    .queryParam("api_key", apiKey)
                                                    .build())
                            .retrieve()
                            .body(JsonNode.class);
            JsonNode school = root.path("results").path(0);
            JsonNode programs = school.path("latest.programs.cip_4_digit");
            // De-dupe by cleaned title (a program repeats once per credential level); keep order.
            Map<String, Option> byTitle = new LinkedHashMap<>();
            for (JsonNode p : programs) {
                String title = cleanTitle(p.path("title").asText(""));
                if (!title.isEmpty()) byTitle.putIfAbsent(title, new Option(title, title));
            }
            List<Option> out = new ArrayList<>(byTitle.values());
            out.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
            return out;
        } catch (Exception ex) {
            log.warn("Scorecard majors lookup failed for id '{}': {}", schoolId, ex.toString());
            return List.of();
        }
    }

    /**
     * The distinct degree levels a school awards, as { value = label = credential title }, ordered
     * lowest → highest credential level. Derived from the same CIP-4 program list as majors,
     * reading each program's {@code credential.level} (1–8) and {@code credential.title}. Empty on
     * blank/failure.
     */
    @RateLimiter(name = "scorecard", fallbackMethod = "degreesForSchoolRateLimited")
    public List<Option> degreesForSchool(String schoolId) {
        if (apiKey.isBlank() || schoolId == null || schoolId.isBlank()) return List.of();
        try {
            JsonNode root =
                    http.get()
                            .uri(
                                    uri ->
                                            uri.path("/schools")
                                                    .queryParam("id", schoolId.trim())
                                                    .queryParam(
                                                            "fields",
                                                            "latest.programs.cip_4_digit.credential.level,latest.programs.cip_4_digit.credential.title")
                                                    .queryParam("per_page", 1)
                                                    .queryParam("api_key", apiKey)
                                                    .build())
                            .retrieve()
                            .body(JsonNode.class);
            JsonNode school = root.path("results").path(0);
            JsonNode programs = school.path("latest.programs.cip_4_digit");
            // One canonical title per credential level (1–8); a TreeMap keeps them ordered
            // lowest → highest (Certificate < Associate < Bachelor's < … < Doctoral).
            Map<Integer, String> byLevel = new TreeMap<>();
            for (JsonNode p : programs) {
                JsonNode credential = p.path("credential");
                int level = credential.path("level").asInt(0);
                String title = cleanTitle(credential.path("title").asText(""));
                if (level > 0 && !title.isEmpty()) byLevel.putIfAbsent(level, title);
            }
            List<Option> out = new ArrayList<>();
            for (String title : byLevel.values()) out.add(new Option(title, title));
            return out;
        } catch (Exception ex) {
            log.warn("Scorecard degrees lookup failed for id '{}': {}", schoolId, ex.toString());
            return List.of();
        }
    }

    /** One school's identity by id; {@code null} on blank key/id, not-found, or any failure. */
    @RateLimiter(name = "scorecard", fallbackMethod = "getSchoolRateLimited")
    public SchoolRef getSchool(String schoolId) {
        if (apiKey.isBlank() || schoolId == null || schoolId.isBlank()) return null;
        try {
            JsonNode root =
                    http.get()
                            .uri(
                                    uri ->
                                            uri.path("/schools")
                                                    .queryParam("id", schoolId.trim())
                                                    .queryParam(
                                                            "fields",
                                                            "id,school.name,school.alias,school.city,school.state")
                                                    .queryParam("per_page", 1)
                                                    .queryParam("api_key", apiKey)
                                                    .build())
                            .retrieve()
                            .body(JsonNode.class);
            JsonNode s = root.path("results").path(0);
            String name = s.path("school.name").asText("");
            if (name.isBlank()) return null;
            return new SchoolRef(
                    schoolId.trim(),
                    name,
                    firstAlias(s.path("school.alias").asText(null)),
                    s.path("school.city").asText(""),
                    s.path("school.state").asText(""));
        } catch (Exception ex) {
            log.warn("Scorecard school lookup failed for id '{}': {}", schoolId, ex.toString());
            return null;
        }
    }

    /** Scorecard's ceiling on {@code per_page}; the directory is fetched in pages this size. */
    public static final int DIRECTORY_PAGE_SIZE = 100;

    /**
     * One page of the whole in-boundary directory, plus upstream's own count of how many there are
     * in total.
     *
     * <p>Paging beats fifty-one per-state counts on both axes: about 20 calls instead of 51, and —
     * the part that matters — the counts and the lists then come from the SAME fetch, so a state's
     * figure cannot disagree with the rows shown under it. {@code total} is what makes the paging
     * safe: the caller compares it against what it actually collected and throws the whole snapshot
     * away on a mismatch, so a page that silently failed shows as "unavailable" rather than as a
     * smaller, entirely plausible directory.
     *
     * <p>{@code null} on blank key, failure, or a payload with no numeric total.
     */
    @RateLimiter(name = "scorecard", fallbackMethod = "directoryPageRateLimited")
    public DirectoryPage directoryPage(int page) {
        if (apiKey.isBlank() || page < 0) return null;
        try {
            JsonNode root =
                    http.get()
                            .uri(
                                    uri ->
                                            withinDirectory(uri.path("/schools"))
                                                    .queryParam(
                                                            "fields",
                                                            "id,school.name,school.city,school.state")
                                                    .queryParam("per_page", DIRECTORY_PAGE_SIZE)
                                                    .queryParam("page", page)
                                                    .queryParam("api_key", apiKey)
                                                    .build())
                            .retrieve()
                            .body(JsonNode.class);
            JsonNode total = root.path("metadata").path("total");
            if (!total.isNumber()) return null;
            // EVERY row, unfiltered — the integrity check downstream counts these against `total`,
            // so dropping anything here would make a healthy page look like a short one.
            List<DirectoryRow> rows = new ArrayList<>();
            for (JsonNode r : root.path("results")) {
                rows.add(
                        new DirectoryRow(
                                r.path("id").asText(""),
                                r.path("school.name").asText(""),
                                r.path("school.city").asText(""),
                                r.path("school.state").asText("")));
            }
            return new DirectoryPage(rows, total.asInt());
        } catch (Exception ex) {
            log.warn("Scorecard directory page {} failed: {}", page, ex.toString());
            return null;
        }
    }

    /** One row exactly as Scorecard returned it, including its state, before any bucketing. */
    public record DirectoryRow(String id, String name, String city, String state) {}

    /** A page of directory rows beside upstream's total for the whole filtered set. */
    public record DirectoryPage(List<DirectoryRow> rows, int total) {}

    // --- Rate-limiter fallbacks -------------------------------------------------------------
    // Reached only when the "scorecard" limiter has no permit left (timeout-duration=0 → fail
    // fast rather than parking a request thread). The methods above already swallow every
    // upstream exception, so RequestNotPermittedException is effectively the only way in here.
    // Each returns the exact degraded value the caller already handles, and never throws.

    List<Option> searchSchoolsRateLimited(String query, int limit, Throwable t) {
        log.warn("Scorecard outbound rate limit hit; degrading school search: {}", t.toString());
        return List.of();
    }

    List<Option> majorsForSchoolRateLimited(String schoolId, Throwable t) {
        log.warn("Scorecard outbound rate limit hit; degrading majors lookup: {}", t.toString());
        return List.of();
    }

    List<Option> degreesForSchoolRateLimited(String schoolId, Throwable t) {
        log.warn("Scorecard outbound rate limit hit; degrading degrees lookup: {}", t.toString());
        return List.of();
    }

    SchoolRef getSchoolRateLimited(String schoolId, Throwable t) {
        log.warn("Scorecard outbound rate limit hit; degrading school lookup: {}", t.toString());
        return null;
    }

    DirectoryPage directoryPageRateLimited(int page, Throwable t) {
        log.warn("Scorecard outbound rate limit hit; degrading directory page: {}", t.toString());
        return null;
    }

    /** Strip the trailing period Scorecard CIP titles carry and collapse whitespace. */
    private static String cleanTitle(String raw) {
        return raw.replaceAll("\\.\\s*$", "").replaceAll("\\s+", " ").trim();
    }

    /**
     * The university's short name, derived from Scorecard's {@code school.alias} — a
     * comma-separated list of alternate names (e.g. {@code "MIT, M.I.T."}). We take the first
     * non-blank, trimmed token as the canonical short name; {@code null} when alias is null/blank
     * or every token is blank.
     */
    private static String firstAlias(String rawAlias) {
        if (rawAlias == null || rawAlias.isBlank()) return null;
        for (String token : rawAlias.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        return null;
    }
}
