package com.CampusToursLive.domain.tour;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which {@link TourFeature}s a guide may pick for an offering, keyed by the offering's {@link
 * TourTopic}. Each topic exposes exactly ten options; the guide selects up to {@link
 * TourFeature#MAX_PER_OFFERING}. This is the single source of truth for feature validation ({@code
 * TourOfferingService}) and for the {@code GET /meta/tour-features} endpoint that serves the
 * options for a topic.
 */
public final class TourFeatureCatalog {

    private static final Map<TourTopic, List<TourFeature>> BY_TOPIC =
            new EnumMap<>(TourTopic.class);

    static {
        BY_TOPIC.put(
                TourTopic.GENERAL_CAMPUS,
                List.of(
                        TourFeature.Q_AND_A,
                        TourFeature.SMALL_GROUP,
                        TourFeature.HIDDEN_SPOTS,
                        TourFeature.PHOTOS_OK,
                        TourFeature.STUDENT_HANGOUTS,
                        TourFeature.LANDMARKS,
                        TourFeature.WHEELCHAIR,
                        TourFeature.MEET_AT_GATE,
                        TourFeature.RAIN_OR_SHINE,
                        TourFeature.INSIDER_TIPS));
        BY_TOPIC.put(
                TourTopic.DORM_HOUSING,
                List.of(
                        TourFeature.DORM_INTERIOR,
                        TourFeature.ROOM_TOUR,
                        TourFeature.SHARED_KITCHENS,
                        TourFeature.LAUNDRY_AMENITIES,
                        TourFeature.MOVE_IN_TIPS,
                        TourFeature.ROOMMATE_ADVICE,
                        TourFeature.PHOTOS_OK,
                        TourFeature.COST_BREAKDOWN,
                        TourFeature.WHEELCHAIR,
                        TourFeature.Q_AND_A));
        BY_TOPIC.put(
                TourTopic.DINING_STUDENT_LIFE,
                List.of(
                        TourFeature.DINING_HALL,
                        TourFeature.FREE_SAMPLES,
                        TourFeature.COFFEE_SPOTS,
                        TourFeature.LATE_NIGHT_EATS,
                        TourFeature.MEAL_PLAN_TIPS,
                        TourFeature.DIETARY_OPTIONS,
                        TourFeature.STUDENT_CLUBS,
                        TourFeature.PHOTOS_OK,
                        TourFeature.Q_AND_A,
                        TourFeature.BUDGET_TIPS));
        BY_TOPIC.put(
                TourTopic.INTERNATIONAL_STUDENT,
                List.of(
                        TourFeature.VISA_TIPS,
                        TourFeature.MULTILINGUAL,
                        TourFeature.TRANSIT_TIPS,
                        TourFeature.HOUSING_HELP,
                        TourFeature.BANKING_SIM,
                        TourFeature.INTL_COMMUNITY,
                        TourFeature.DIETARY_OPTIONS,
                        TourFeature.Q_AND_A,
                        TourFeature.CULTURAL_CLUBS,
                        TourFeature.ORIENTATION_TIPS));
        BY_TOPIC.put(
                TourTopic.MAJOR_SPECIFIC,
                List.of(
                        TourFeature.LAB_ACCESS,
                        TourFeature.LECTURE_SIT_IN,
                        TourFeature.MEET_FACULTY,
                        TourFeature.RESEARCH_SPOTLIGHT,
                        TourFeature.LIBRARY_RESOURCES,
                        TourFeature.COURSE_ADVICE,
                        TourFeature.CAREER_TIPS,
                        TourFeature.PROJECT_SHOWCASE,
                        TourFeature.Q_AND_A,
                        TourFeature.STUDY_SPACES));
        BY_TOPIC.put(
                TourTopic.PARENT_FOCUSED,
                List.of(
                        TourFeature.SAFETY_OVERVIEW,
                        TourFeature.COST_AID_INFO,
                        TourFeature.HOUSING_WALKTHROUGH,
                        TourFeature.DINING_OVERVIEW,
                        TourFeature.HEALTH_SERVICES,
                        TourFeature.TRANSIT_PARKING,
                        TourFeature.SLOWER_PACE,
                        TourFeature.Q_AND_A,
                        TourFeature.FAMILY_FRIENDLY,
                        TourFeature.PHOTOS_OK));
        BY_TOPIC.put(
                TourTopic.FRESHMAN,
                List.of(
                        TourFeature.FIRST_YEAR_DORMS,
                        TourFeature.ORIENTATION_TIPS,
                        TourFeature.WHERE_TO_EAT,
                        TourFeature.STUDY_SPOTS,
                        TourFeature.CLUBS_ACTIVITIES,
                        TourFeature.GETTING_AROUND,
                        TourFeature.MEET_UPPERCLASSMEN,
                        TourFeature.SURVIVAL_TIPS,
                        TourFeature.Q_AND_A,
                        TourFeature.PHOTOS_OK));
        BY_TOPIC.put(
                TourTopic.TRANSFER,
                List.of(
                        TourFeature.CREDIT_TRANSFER_TIPS,
                        TourFeature.TRANSFER_HOUSING,
                        TourFeature.ADVISING_RESOURCES,
                        TourFeature.TRANSFER_COMMUNITY,
                        TourFeature.FAST_TRACK_ORIENTATION,
                        TourFeature.COURSE_PLANNING,
                        TourFeature.CAREER_SERVICES,
                        TourFeature.Q_AND_A,
                        TourFeature.GET_CONNECTED,
                        TourFeature.INSIDER_TIPS));
    }

    private TourFeatureCatalog() {}

    /**
     * The ordered list of features a guide may pick for the given topic (empty if topic is null).
     */
    public static List<TourFeature> allowedFor(TourTopic topic) {
        if (topic == null) return List.of();
        return BY_TOPIC.getOrDefault(topic, List.of());
    }

    /** True if {@code feature} is selectable for {@code topic}. */
    public static boolean isAllowed(TourTopic topic, TourFeature feature) {
        return allowedFor(topic).contains(feature);
    }

    /** Distinct, catalog-ordered features for a topic (dedupes a client-supplied selection). */
    public static Set<TourFeature> allowedSet(TourTopic topic) {
        return new LinkedHashSet<>(allowedFor(topic));
    }
}
