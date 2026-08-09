package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CampusToursLive.domain.guide.EnrollmentYearRules;
import com.CampusToursLive.domain.tour.TourFeatureCatalog;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.integration.scorecard.SchoolDirectory;
import com.CampusToursLive.web.MetaController.Option;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * MetaController.tourTopics is the single source of truth for the topic vocabulary. This locks two
 * things: every TourTopic enum value is exposed (so adding an enum constant without exposing it
 * fails), and the value is the enum name with a human label. The getOrDefault fallback means a new
 * unmapped topic still appears (labelled with its name) rather than vanishing.
 */
class MetaControllerTest {

    /** Stub live directory so the controller's static-vocabulary endpoints test without HTTP. */
    private static final SchoolDirectory SCHOOLS =
            new SchoolDirectory() {
                @Override
                public List<Option> searchSchools(String query, int limit) {
                    return List.of(new Option("243744", "Stanford University — Stanford, CA"));
                }

                @Override
                public List<Option> majorsForSchool(String schoolId) {
                    return List.of(new Option("Computer Science", "Computer Science"));
                }

                @Override
                public List<Option> degreesForSchool(String schoolId) {
                    return List.of(new Option("Bachelor's Degree", "Bachelor's Degree"));
                }

                @Override
                public SchoolDirectory.SchoolRef getSchool(String schoolId) {
                    return new SchoolDirectory.SchoolRef(
                            schoolId, "Stanford University", "Stanford", "Stanford", "CA");
                }
            };

    /** These tests don't exercise the enrolment-year endpoint; a fixed clock is enough. */
    private static final EnrollmentYearRules RULES =
            new EnrollmentYearRules(
                    Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void tourTopics_exposesEveryTopicByName() {
        List<Option> topics = new MetaController(SCHOOLS, RULES).tourTopics().data();

        assertEquals(TourTopic.values().length, topics.size());
        List<String> values = topics.stream().map(Option::value).toList();
        for (TourTopic t : TourTopic.values()) {
            assertTrue(values.contains(t.name()), "missing topic: " + t.name());
        }
    }

    @Test
    void tourTopics_mapsKnownTopicsToLabels() {
        List<Option> topics = new MetaController(SCHOOLS, RULES).tourTopics().data();
        Option general =
                topics.stream()
                        .filter(o -> o.value().equals(TourTopic.GENERAL_CAMPUS.name()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("General campus", general.label());
    }

    @Test
    void tourFeatures_exposesTenLabelledOptionsPerTopic() {
        Map<String, List<Option>> byTopic =
                new MetaController(SCHOOLS, RULES).tourFeatures().data();

        assertEquals(TourTopic.values().length, byTopic.size());
        for (TourTopic topic : TourTopic.values()) {
            List<Option> options = byTopic.get(topic.name());
            assertEquals(10, options.size(), "topic " + topic + " must expose 10 features");
            // values match the catalog codes; labels are non-blank
            List<String> codes =
                    TourFeatureCatalog.allowedFor(topic).stream().map(Enum::name).toList();
            assertEquals(codes, options.stream().map(Option::value).toList());
            assertTrue(options.stream().allMatch(o -> !o.label().isBlank()));
        }
    }

    @Test
    void languages_exposeEnglishDisplayNames() {
        List<Option> langs = new MetaController(SCHOOLS, RULES).languages().data();
        assertTrue(langs.size() >= 5);
        Option zh = langs.stream().filter(o -> o.value().equals("zh")).findFirst().orElseThrow();
        assertEquals("Chinese", zh.label());
        Option en = langs.stream().filter(o -> o.value().equals("en-US")).findFirst().orElseThrow();
        assertEquals("English", en.label());
    }

    @Test
    void universities_delegatesToTheLiveDirectory() {
        List<Option> results = new MetaController(SCHOOLS, RULES).universities("stanford").data();
        assertEquals(1, results.size());
        assertEquals("243744", results.get(0).value());
        assertTrue(results.get(0).label().startsWith("Stanford"));
    }

    @Test
    void majors_delegatesToTheLiveDirectory() {
        List<Option> results = new MetaController(SCHOOLS, RULES).majors("243744").data();
        assertEquals("Computer Science", results.get(0).label());
    }

    @Test
    void degrees_delegatesToTheLiveDirectory() {
        List<Option> results = new MetaController(SCHOOLS, RULES).degrees("243744").data();
        assertEquals("Bachelor's Degree", results.get(0).label());
    }
}
