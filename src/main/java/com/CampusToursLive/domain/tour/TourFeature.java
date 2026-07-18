package com.CampusToursLive.domain.tour;

/**
 * Controlled catalog of tour "feature" chips a guide may attach to an offering at creation time.
 * Which features are selectable depends on the tour's {@link TourTopic} — see {@link
 * TourFeatureCatalog}. The set a guide picks is capped (see {@code TourOfferingService}). Stored on
 * {@code tour_offerings.features} as a JSON array of these enum names (mirrors {@code specialties}
 * / {@code languages}); the display label lives here so backend + OpenAPI stay the source of truth.
 */
public enum TourFeature {
    // Shared across several topics
    Q_AND_A("Q&A included"),
    SMALL_GROUP("Small group"),
    PHOTOS_OK("Photos allowed"),
    WHEELCHAIR("Wheelchair accessible"),
    INSIDER_TIPS("Insider tips"),
    ORIENTATION_TIPS("Orientation tips"),
    DIETARY_OPTIONS("Dietary options"),

    // GENERAL_CAMPUS
    HIDDEN_SPOTS("Hidden study spots"),
    STUDENT_HANGOUTS("Student hangouts"),
    LANDMARKS("Landmarks & history"),
    MEET_AT_GATE("Meet at main gate"),
    RAIN_OR_SHINE("Rain or shine"),

    // DORM_HOUSING
    DORM_INTERIOR("Dorm interior access"),
    ROOM_TOUR("Sample room tour"),
    SHARED_KITCHENS("Shared kitchens"),
    LAUNDRY_AMENITIES("Laundry & amenities"),
    MOVE_IN_TIPS("Move-in tips"),
    ROOMMATE_ADVICE("Roommate advice"),
    COST_BREAKDOWN("Cost breakdown"),

    // DINING_STUDENT_LIFE
    DINING_HALL("Dining hall visit"),
    FREE_SAMPLES("Free samples"),
    COFFEE_SPOTS("Coffee spots"),
    LATE_NIGHT_EATS("Late-night eats"),
    MEAL_PLAN_TIPS("Meal plan tips"),
    STUDENT_CLUBS("Student clubs"),
    BUDGET_TIPS("Budget tips"),

    // INTERNATIONAL_STUDENT
    VISA_TIPS("Visa & CPT tips"),
    MULTILINGUAL("Multilingual guide"),
    TRANSIT_TIPS("Airport & transit tips"),
    HOUSING_HELP("Housing help"),
    BANKING_SIM("Banking & SIM setup"),
    INTL_COMMUNITY("International community"),
    CULTURAL_CLUBS("Cultural clubs"),

    // MAJOR_SPECIFIC
    LAB_ACCESS("Lab & studio access"),
    LECTURE_SIT_IN("Lecture sit-in"),
    MEET_FACULTY("Meet faculty (if available)"),
    RESEARCH_SPOTLIGHT("Research spotlights"),
    LIBRARY_RESOURCES("Library & resources"),
    COURSE_ADVICE("Course advice"),
    CAREER_TIPS("Career & internship tips"),
    PROJECT_SHOWCASE("Project showcase"),
    STUDY_SPACES("Study spaces"),

    // PARENT_FOCUSED
    SAFETY_OVERVIEW("Safety overview"),
    COST_AID_INFO("Cost & aid info"),
    HOUSING_WALKTHROUGH("Housing walkthrough"),
    DINING_OVERVIEW("Dining overview"),
    HEALTH_SERVICES("Health services"),
    TRANSIT_PARKING("Transit & parking"),
    SLOWER_PACE("Slower pace"),
    FAMILY_FRIENDLY("Family-friendly"),

    // FRESHMAN
    FIRST_YEAR_DORMS("First-year dorms"),
    WHERE_TO_EAT("Where to eat"),
    STUDY_SPOTS("Study spots"),
    CLUBS_ACTIVITIES("Clubs & activities"),
    GETTING_AROUND("Getting around"),
    MEET_UPPERCLASSMEN("Meet upperclassmen"),
    SURVIVAL_TIPS("Survival tips"),

    // TRANSFER
    CREDIT_TRANSFER_TIPS("Credit transfer tips"),
    TRANSFER_HOUSING("Transfer housing"),
    ADVISING_RESOURCES("Advising resources"),
    TRANSFER_COMMUNITY("Transfer community"),
    FAST_TRACK_ORIENTATION("Fast-track orientation"),
    COURSE_PLANNING("Course planning"),
    CAREER_SERVICES("Career services"),
    GET_CONNECTED("Getting connected");

    /** Max features a guide may attach to one offering. */
    public static final int MAX_PER_OFFERING = 3;

    private final String label;

    TourFeature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
