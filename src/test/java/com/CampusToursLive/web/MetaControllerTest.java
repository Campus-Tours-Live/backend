package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.web.MetaController.Option;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MetaController.tourTopics is the single source of truth for the topic vocabulary. This locks two
 * things: every TourTopic enum value is exposed (so adding an enum constant without exposing it
 * fails), and the value is the enum name with a human label. The getOrDefault fallback means a new
 * unmapped topic still appears (labelled with its name) rather than vanishing.
 */
class MetaControllerTest {

    @Test
    void tourTopics_exposesEveryTopicByName() {
        List<Option> topics = new MetaController().tourTopics().data();

        assertEquals(TourTopic.values().length, topics.size());
        List<String> values = topics.stream().map(Option::value).toList();
        for (TourTopic t : TourTopic.values()) {
            assertTrue(values.contains(t.name()), "missing topic: " + t.name());
        }
    }

    @Test
    void tourTopics_mapsKnownTopicsToLabels() {
        List<Option> topics = new MetaController().tourTopics().data();
        Option general =
                topics.stream()
                        .filter(o -> o.value().equals(TourTopic.GENERAL_CAMPUS.name()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("General campus", general.label());
    }
}
