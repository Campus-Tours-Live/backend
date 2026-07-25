package com.CampusToursLive.integration.scorecard;

import com.CampusToursLive.integration.scorecard.SchoolDirectory.SchoolRef;
import com.CampusToursLive.web.MetaController.Option;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    /** Search schools by (partial) name. Empty list on blank key/query or any failure. */
    @RateLimiter(name = "scorecard", fallbackMethod = "searchSchoolsRateLimited")
    public List<Option> searchSchools(String query, int limit, int page) {
        if (apiKey.isBlank() || query == null || query.isBlank()) return List.of();
        int perPage = Math.min(Math.max(limit, 1), 50);
        int safePage = Math.max(page, 0);
        try {
            JsonNode root =
                    http.get()
                            .uri(
                                    uri ->
                                            uri.path("/schools")
                                                    .queryParam("school.name", query.trim())
                                                    .queryParam(
                                                            "fields",
                                                            "id,school.name,school.city,school.state")
                                                    .queryParam("per_page", perPage)
                                                    .queryParam("page", safePage)
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
                                                            "id,school.name,school.city,school.state")
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
                    s.path("school.city").asText(""),
                    s.path("school.state").asText(""));
        } catch (Exception ex) {
            log.warn("Scorecard school lookup failed for id '{}': {}", schoolId, ex.toString());
            return null;
        }
    }

    // --- Rate-limiter fallbacks -------------------------------------------------------------
    // Reached only when the "scorecard" limiter has no permit left (timeout-duration=0 → fail
    // fast rather than parking a request thread). The methods above already swallow every
    // upstream exception, so RequestNotPermittedException is effectively the only way in here.
    // Each returns the exact degraded value the caller already handles, and never throws.

    List<Option> searchSchoolsRateLimited(String query, int limit, int page, Throwable t) {
        log.warn("Scorecard outbound rate limit hit; degrading school search: {}", t.toString());
        return List.of();
    }

    List<Option> majorsForSchoolRateLimited(String schoolId, Throwable t) {
        log.warn("Scorecard outbound rate limit hit; degrading majors lookup: {}", t.toString());
        return List.of();
    }

    SchoolRef getSchoolRateLimited(String schoolId, Throwable t) {
        log.warn("Scorecard outbound rate limit hit; degrading school lookup: {}", t.toString());
        return null;
    }

    /** Strip the trailing period Scorecard CIP titles carry and collapse whitespace. */
    private static String cleanTitle(String raw) {
        return raw.replaceAll("\\.\\s*$", "").replaceAll("\\s+", " ").trim();
    }
}
