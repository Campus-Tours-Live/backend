package com.CampusToursLive.integration.scorecard;

import com.CampusToursLive.web.MetaController.Option;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link SchoolDirectory} over the U.S. Dept. of Education College Scorecard API (IPEDS-derived).
 * The API key is server-side only (never exposed to the browser). Failures degrade to an empty list
 * so a flaky upstream never breaks onboarding — the caller shows "no matches" rather than erroring.
 */
@Component
public class ScorecardClient implements SchoolDirectory {

    private static final Logger log = LoggerFactory.getLogger(ScorecardClient.class);

    private final RestClient http;
    private final String apiKey;

    public ScorecardClient(
            @Value("${app.scorecard.base-url:https://api.data.gov/ed/collegescorecard/v1}")
                    String baseUrl,
            @Value("${app.scorecard.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5000);
        rf.setReadTimeout(15000);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public List<Option> searchSchools(String query, int limit) {
        if (apiKey.isBlank() || query == null || query.isBlank()) return List.of();
        int perPage = Math.min(Math.max(limit, 1), 50);
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

    @Override
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

    @Override
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

    /** Strip the trailing period Scorecard CIP titles carry and collapse whitespace. */
    private static String cleanTitle(String raw) {
        return raw.replaceAll("\\.\\s*$", "").replaceAll("\\s+", " ").trim();
    }
}
