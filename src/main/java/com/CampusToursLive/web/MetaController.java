package com.CampusToursLive.web;

import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.web.dto.ApiEnvelope;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reference / lookup data for the UI (BFF maps /v1/meta/* → here). Single source of truth for
 * controlled vocabularies like tour topics, so the frontend never hardcodes the list.
 */
@RestController
@RequestMapping("/meta")
public class MetaController {

    public record Option(String value, String label) {}

    private static final Map<TourTopic, String> TOPIC_LABELS =
            Map.of(
                    TourTopic.GENERAL_CAMPUS, "General campus",
                    TourTopic.DORM_HOUSING, "Dorms & housing",
                    TourTopic.DINING_STUDENT_LIFE, "Dining & student life",
                    TourTopic.MAJOR_SPECIFIC, "Major-specific",
                    TourTopic.INTERNATIONAL_STUDENT, "International student",
                    TourTopic.PARENT_FOCUSED, "Parent-focused",
                    TourTopic.FRESHMAN, "Freshman",
                    TourTopic.TRANSFER, "Transfer");

    @GetMapping("/tour-topics")
    public ApiEnvelope<List<Option>> tourTopics() {
        List<Option> topics =
                java.util.Arrays.stream(TourTopic.values())
                        .map(t -> new Option(t.name(), TOPIC_LABELS.getOrDefault(t, t.name())))
                        .toList();
        return ApiEnvelope.of(topics);
    }
}
