package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.guide.EnrollmentYearRules;
import com.CampusToursLive.integration.scorecard.SchoolDirectory;
import com.CampusToursLive.web.doc.ApiExamples;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice test for {@code GET /meta/enrollment-years}. Modelled on {@link
 * TourControllerTest}'s {@code @WebMvcTest} wiring (a plain public GET, no {@code CurrentUser}
 * involved) rather than {@link MetaControllerTest}, which is a hand-constructed unit test with no
 * MockMvc.
 *
 * <p>{@code @WebMvcTest} only scans the web layer, so {@link EnrollmentYearRules} (a plain
 * {@code @Component} in the domain package) is not picked up automatically. It is imported
 * explicitly so this test exercises the REAL rule table — not a mock — while its {@link Clock}
 * dependency is swapped for the mutable test clock below.
 */
@WebMvcTest(
        controllers = MetaController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
@Import(EnrollmentYearRules.class)
class MetaControllerEnrollmentYearsTest {

    private static final Instant MID_2026 = Instant.parse("2026-07-29T12:00:00Z");

    @Autowired private MockMvc mvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private SchoolDirectory schools;

    /**
     * A MUTABLE clock bean, not a Clock.fixed. The slice's application context is reused across
     * test methods, so a singleton `Clock.fixed` cannot be swapped per test — "re-register the
     * bean" is not something a test method can do. Moving the instant on a shared mutable bean is,
     * and it keeps the tests order-independent as long as every one of them sets what it needs (see
     * the @BeforeEach reset below).
     */
    static final class MutableClock extends Clock {
        private Instant instant = MID_2026;

        void setInstant(Instant value) {
            this.instant = value;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        MutableClock clock() {
            return new MutableClock();
        }
    }

    @Autowired private MutableClock clock;

    /**
     * Order-independence: the New Year test moves the clock, and without this reset whichever test
     * JUnit happens to run next would inherit 31 December. A shared mutable bean is only safe when
     * every test starts from a known instant.
     */
    @BeforeEach
    void resetClock() {
        clock.setInstant(MID_2026);
    }

    @Test
    void returnsTheEntryYearWindowAndTheOrderedDegreeTable() throws Exception {
        mvc.perform(get("/meta/enrollment-years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryYear.min").value(2016))
                .andExpect(jsonPath("$.data.entryYear.max").value(2027))
                .andExpect(jsonPath("$.data.defaultMaxYearsToGraduate").value(8))
                // Order is part of the contract: clients apply first-hit over this array.
                .andExpect(jsonPath("$.data.maxYearsToGraduate[0].years").value(9))
                .andExpect(jsonPath("$.data.maxYearsToGraduate[0].matches[0]").value("doctor"))
                .andExpect(jsonPath("$.data.maxYearsToGraduate[1].years").value(3))
                .andExpect(jsonPath("$.data.maxYearsToGraduate[2].years").value(6))
                .andExpect(jsonPath("$.data.maxYearsToGraduate[2].matches[0]").value("bachelor"))
                .andExpect(jsonPath("$.data.maxYearsToGraduate[3].years").value(3));
    }

    @Test
    void cacheControlIsTheTwentyFourHourCeilingOnAnOrdinaryDay() throws Exception {
        mvc.perform(get("/meta/enrollment-years"))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"));
    }

    /** The whole rollover scheme rests on the age shrinking by itself near the boundary. */
    @Test
    void cacheControlContractsToTheYearBoundaryOnNewYearsEve() throws Exception {
        clock.setInstant(Instant.parse("2026-12-31T23:00:00Z"));

        mvc.perform(get("/meta/enrollment-years"))
                .andExpect(header().string("Cache-Control", "public, max-age=3600"))
                // The body must describe the SAME year the header expires with — one snapshot.
                .andExpect(jsonPath("$.data.entryYear.max").value(2027));
    }

    /**
     * The documented example is a hand-written literal — it cannot read the rule table, so it is
     * the one place a stale rule number can survive a change (spec I1's stated exception). Pinning
     * it here means editing bachelor 6 → 5 breaks this test instead of silently leaving the
     * published contract advertising the old number to whoever reads the docs.
     */
    @Test
    void theDocumentedExampleMatchesAWireResponse() throws Exception {
        String actual =
                mvc.perform(get("/meta/enrollment-years"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode documented = objectMapper.readTree(ApiExamples.ENROLLMENT_YEARS).get("data");
        JsonNode served = objectMapper.readTree(actual).get("data");

        // `meta` differs per request (requestId, timestamp) — compare the payload only.
        assertEquals(documented, served);
    }
}
