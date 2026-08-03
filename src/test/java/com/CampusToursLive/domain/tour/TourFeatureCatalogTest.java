package com.CampusToursLive.domain.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TourFeatureCatalogTest {

    @Test
    void everyTopicOffersTenDistinctFeatures() {
        for (TourTopic topic : TourTopic.values()) {
            List<TourFeature> opts = TourFeatureCatalog.allowedFor(topic);
            assertEquals(10, opts.size(), "topic " + topic + " must offer 10 features");
            assertEquals(
                    10, opts.stream().distinct().count(), "topic " + topic + " has duplicates");
        }
    }

    @Test
    void isAllowed_reflectsTheCatalog() {
        assertTrue(TourFeatureCatalog.isAllowed(TourTopic.GENERAL_CAMPUS, TourFeature.Q_AND_A));
        assertFalse(
                TourFeatureCatalog.isAllowed(TourTopic.GENERAL_CAMPUS, TourFeature.DORM_INTERIOR));
        assertTrue(TourFeatureCatalog.allowedFor(null).isEmpty());
    }

    @Test
    void allowedSet_preservesEveryOption() {
        assertEquals(
                TourFeatureCatalog.allowedFor(TourTopic.FRESHMAN).size(),
                TourFeatureCatalog.allowedSet(TourTopic.FRESHMAN).size());
    }

    @Test
    void everyFeatureHasANonBlankLabel_andCapIsThree() {
        assertEquals(3, TourFeature.MAX_PER_OFFERING);
        assertEquals("Q&A included", TourFeature.Q_AND_A.label());
        for (TourFeature f : TourFeature.values()) {
            assertNotNull(f.label());
            assertFalse(f.label().isBlank());
        }
    }
}
