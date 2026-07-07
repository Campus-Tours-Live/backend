package com.CampusToursLive.web.doc;

/**
 * OpenAPI response-body examples, kept as compile-time string constants so they can be referenced
 * from {@code @ExampleObject(value = ...)} annotations on the controllers. Declared as an interface
 * (implicitly {@code public static final}) with only constant fields so it carries no methods or
 * static initializer — it contributes nothing to JaCoCo coverage.
 *
 * <p>Every example is the full {@code { "data": ..., "meta": ... }} success envelope, or an RFC
 * 7807 {@code problem+json} error body, matching what the endpoint actually returns (Contract B).
 */
public interface ApiExamples {

    String META =
            "\"meta\":{\"requestId\":\"3f1c9a2e-7d40-4b2a-9c11-5b8e0a2f6d10\","
                    + "\"timestamp\":\"2026-07-07T12:00:00Z\"}";

    // --- Error bodies (RFC 7807 problem+json) ---

    String PROBLEM_401 =
            "{\"type\":\"about:blank\",\"title\":\"No valid principal\",\"status\":401,"
                    + "\"detail\":\"Authentication is required.\"}";

    String PROBLEM_403 =
            "{\"type\":\"about:blank\",\"title\":\"Missing required role\",\"status\":403,"
                    + "\"detail\":\"The caller does not hold the role this endpoint requires.\"}";

    String PROBLEM_404 =
            "{\"type\":\"about:blank\",\"title\":\"Not found\",\"status\":404,"
                    + "\"detail\":\"The requested resource does not exist.\"}";

    String PROBLEM_409 =
            "{\"type\":\"about:blank\",\"title\":\"This resource was modified by another request —"
                    + " please retry\",\"status\":409}";

    String PROBLEM_422 =
            "{\"type\":\"about:blank\",\"title\":\"Validation failed\",\"status\":422,"
                    + "\"detail\":\"One or more fields are invalid.\"}";

    // --- Success envelopes ---

    String BOOKING_DETAIL =
            "{\"data\":{\"id\":\"b1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"status\":\"WAITING_FOR_GUIDE\",\"scheduledAt\":\"2026-08-01T15:00:00Z\","
                    + "\"timezone\":\"America/Los_Angeles\","
                    + "\"offeringId\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"offeringTitle\":\"North Campus highlights\",\"guideName\":\"Maya Chen\","
                    + "\"guideResponseDeadline\":\"2026-07-30T15:00:00Z\","
                    + "\"universityName\":\"North Coast University\",\"durationMin\":60,"
                    + "\"priceCents\":4200,\"currency\":\"USD\"},"
                    + META
                    + "}";

    String BOOKING_LIST =
            "{\"data\":[{\"id\":\"b1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"status\":\"CONFIRMED\",\"scheduledAt\":\"2026-08-01T15:00:00Z\","
                    + "\"timezone\":\"America/Los_Angeles\","
                    + "\"offeringId\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"offeringTitle\":\"North Campus highlights\",\"guideName\":\"Maya Chen\","
                    + "\"guideResponseDeadline\":null,"
                    + "\"universityName\":\"North Coast University\",\"durationMin\":60,"
                    + "\"priceCents\":4200,\"currency\":\"USD\"}],"
                    + META
                    + "}";

    String NEXT_TOUR_EMPTY = "{\"data\":null," + META + "}";

    String BOOKING_CANCELLED =
            "{\"data\":{\"id\":\"b1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"status\":\"CANCELLED\",\"scheduledAt\":\"2026-08-01T15:00:00Z\","
                    + "\"timezone\":\"America/Los_Angeles\","
                    + "\"offeringId\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"offeringTitle\":\"North Campus highlights\",\"guideName\":\"Maya Chen\","
                    + "\"guideResponseDeadline\":null,"
                    + "\"universityName\":\"North Coast University\",\"durationMin\":60,"
                    + "\"priceCents\":4200,\"currency\":\"USD\"},"
                    + META
                    + "}";

    String CART_LIST =
            "{\"data\":[{\"id\":\"b1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"status\":\"DRAFT\",\"scheduledAt\":\"2026-08-01T15:00:00Z\","
                    + "\"timezone\":\"America/Los_Angeles\","
                    + "\"offeringId\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"offeringTitle\":\"North Campus highlights\",\"guideName\":\"Maya Chen\","
                    + "\"guideResponseDeadline\":null,"
                    + "\"universityName\":\"North Coast University\",\"durationMin\":60,"
                    + "\"priceCents\":4200,\"currency\":\"USD\"}],"
                    + META
                    + "}";

    String CART_EMPTY = "{\"data\":[]," + META + "}";

    String CART_ITEM_DRAFT =
            "{\"data\":{\"id\":\"b1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"status\":\"DRAFT\",\"scheduledAt\":\"2026-08-01T15:00:00Z\","
                    + "\"timezone\":\"America/Los_Angeles\","
                    + "\"offeringId\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"offeringTitle\":\"North Campus highlights\",\"guideName\":\"Maya Chen\","
                    + "\"guideResponseDeadline\":null,"
                    + "\"universityName\":\"North Coast University\",\"durationMin\":60,"
                    + "\"priceCents\":4200,\"currency\":\"USD\"},"
                    + META
                    + "}";

    String CART_CHECKOUT_RESULT =
            "{\"data\":[{\"id\":\"b1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"status\":\"WAITING_FOR_GUIDE\",\"scheduledAt\":\"2026-08-01T15:00:00Z\","
                    + "\"timezone\":\"America/Los_Angeles\","
                    + "\"offeringId\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"offeringTitle\":\"North Campus highlights\",\"guideName\":\"Maya Chen\","
                    + "\"guideResponseDeadline\":\"2026-07-30T15:00:00Z\","
                    + "\"universityName\":\"North Coast University\",\"durationMin\":60,"
                    + "\"priceCents\":4200,\"currency\":\"USD\"}],"
                    + META
                    + "}";

    String PENDING_ACTIONS =
            "{\"data\":{\"paymentsToFinish\":1,\"waitingForGuide\":2,\"reviewsToWrite\":0},"
                    + META
                    + "}";

    String GUIDE_PROFILE =
            "{\"data\":{\"userId\":\"11111111-0000-4000-8000-000000000001\","
                    + "\"firstName\":\"Maya\",\"lastName\":\"Chen\",\"displayName\":\"Maya Chen\","
                    + "\"email\":\"maya.chen@ncu.edu\",\"accountStatus\":\"ACTIVE\","
                    + "\"universityId\":\"u1a2c3d4-0000-4000-8000-000000000003\","
                    + "\"universityName\":\"North Coast University\","
                    + "\"universityShortName\":\"NCU\",\"major\":\"Marine Biology\","
                    + "\"classYear\":\"Junior\",\"bio\":\"Third-year student and campus tour"
                    + " lead.\","
                    + "\"languages\":[\"en-US\",\"zh-CN\"],\"specialties\":[\"DORM_HOUSING\"],"
                    + "\"basePriceCents\":4200,\"currency\":\"USD\","
                    + "\"applicationStatus\":\"APPROVED\",\"verificationStatus\":\"VERIFIED\"},"
                    + META
                    + "}";

    String PARTICIPANT_PROFILE =
            "{\"data\":{\"userId\":\"22222222-0000-4000-8000-000000000001\","
                    + "\"firstName\":\"Sam\",\"lastName\":\"Rivera\",\"displayName\":\"Sam Rivera\","
                    + "\"email\":\"sam.rivera@example.com\",\"preferredLanguage\":\"en-US\","
                    + "\"timezone\":\"America/New_York\",\"participantType\":\"PROSPECTIVE_STUDENT\","
                    + "\"gradeLevel\":\"HIGH_SCHOOL_SENIOR\",\"intendedMajor\":\"Computer Science\","
                    + "\"guardianRequired\":false,\"topicsOfInterest\":[\"DORM_HOUSING\"],"
                    + "\"universitiesOfInterest\":[\"u1a2c3d4-0000-4000-8000-000000000003\"],"
                    + "\"accessibilityPreferences\":null},"
                    + META
                    + "}";

    String ME =
            "{\"data\":{\"id\":\"22222222-0000-4000-8000-000000000001\","
                    + "\"roles\":[\"PARTICIPANT\"],\"activeRole\":\"PARTICIPANT\","
                    + "\"participantType\":\"PROSPECTIVE_STUDENT\",\"guideStatus\":null,"
                    + "\"firstName\":\"Sam\",\"lastName\":\"Rivera\",\"displayName\":\"Sam Rivera\","
                    + "\"email\":\"sam.rivera@example.com\",\"accountStatus\":\"ACTIVE\","
                    + "\"ageBand\":\"ADULT\",\"createdAt\":\"2026-01-15T09:30:00Z\"},"
                    + META
                    + "}";

    String TOUR_TOPICS =
            "{\"data\":[{\"value\":\"GENERAL_CAMPUS\",\"label\":\"General campus\"},"
                    + "{\"value\":\"DORM_HOUSING\",\"label\":\"Dorms & housing\"}],"
                    + META
                    + "}";

    String UNIVERSITY_LIST =
            "{\"data\":[{\"id\":\"u1a2c3d4-0000-4000-8000-000000000003\","
                    + "\"slug\":\"north-coast\",\"name\":\"North Coast University\","
                    + "\"shortName\":\"NCU\",\"city\":\"Arcata\",\"region\":\"CA\"}],"
                    + META
                    + "}";

    String TOUR_OFFERING =
            "{\"data\":{\"id\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"title\":\"North Campus highlights\",\"slug\":\"north-campus-highlights\","
                    + "\"status\":\"ACTIVE\",\"topic\":\"GENERAL_CAMPUS\","
                    + "\"universityId\":\"u1a2c3d4-0000-4000-8000-000000000003\","
                    + "\"durationMin\":60,\"priceCents\":4200,\"currency\":\"USD\","
                    + "\"description\":\"A guided walk through the north campus.\"},"
                    + META
                    + "}";

    String TOUR_OFFERING_LIST =
            "{\"data\":[{\"id\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"title\":\"North Campus highlights\",\"slug\":\"north-campus-highlights\","
                    + "\"status\":\"DRAFT\",\"topic\":\"GENERAL_CAMPUS\","
                    + "\"universityId\":\"u1a2c3d4-0000-4000-8000-000000000003\","
                    + "\"durationMin\":60,\"priceCents\":4200,\"currency\":\"USD\","
                    + "\"description\":\"A guided walk through the north campus.\"}],"
                    + META
                    + "}";

    String TOUR_SUMMARY_LIST =
            "{\"data\":[{\"id\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"title\":\"North Campus highlights\",\"slug\":\"north-campus-highlights\","
                    + "\"topic\":\"GENERAL_CAMPUS\","
                    + "\"universityId\":\"u1a2c3d4-0000-4000-8000-000000000003\","
                    + "\"universityName\":\"North Coast University\","
                    + "\"guideId\":\"11111111-0000-4000-8000-000000000001\","
                    + "\"guideDisplayName\":\"Maya Chen\",\"durationMin\":60,\"priceCents\":4200,"
                    + "\"currency\":\"USD\",\"avgRating\":4.5,\"reviewCount\":12}],"
                    + META
                    + "}";

    String TOUR_DETAIL =
            "{\"data\":{\"id\":\"o1a2c3d4-0000-4000-8000-000000000002\","
                    + "\"title\":\"North Campus highlights\",\"slug\":\"north-campus-highlights\","
                    + "\"topic\":\"GENERAL_CAMPUS\","
                    + "\"description\":\"A guided walk through the north campus.\","
                    + "\"languages\":[\"en-US\"],"
                    + "\"universityId\":\"u1a2c3d4-0000-4000-8000-000000000003\","
                    + "\"universityName\":\"North Coast University\","
                    + "\"universitySlug\":\"north-coast\",\"universityCity\":\"Arcata\","
                    + "\"universityRegion\":\"CA\","
                    + "\"guideId\":\"11111111-0000-4000-8000-000000000001\","
                    + "\"guideDisplayName\":\"Maya Chen\","
                    + "\"guideBio\":\"Third-year student and campus tour lead.\","
                    + "\"durationMin\":60,\"priceCents\":4200,\"currency\":\"USD\","
                    + "\"avgRating\":4.5,\"reviewCount\":12},"
                    + META
                    + "}";

    String AVAILABILITY_RULE =
            "{\"data\":{\"id\":\"a1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"dayOfWeek\":1,\"startLocal\":\"09:00\",\"endLocal\":\"17:00\","
                    + "\"timezone\":\"America/Los_Angeles\",\"effectiveFrom\":\"2026-06-01\","
                    + "\"effectiveTo\":null,\"active\":true,"
                    + "\"createdAt\":\"2026-06-01T12:00:00Z\"},"
                    + META
                    + "}";

    String AVAILABILITY_EXCEPTION =
            "{\"data\":{\"id\":\"e1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"exceptionDate\":\"2026-07-04\",\"type\":\"UNAVAILABLE_ALL_DAY\","
                    + "\"startLocal\":null,\"endLocal\":null,\"reason\":\"Holiday\","
                    + "\"createdAt\":\"2026-06-15T12:00:00Z\"},"
                    + META
                    + "}";

    String BOOKING_SETTINGS =
            "{\"data\":{\"acceptanceMode\":\"MANUAL\",\"responseDeadlineMin\":90,"
                    + "\"minNoticeMin\":1440,\"maxAdvanceDays\":30,\"bufferBeforeMin\":0,"
                    + "\"bufferAfterMin\":15,\"durationsOffered\":[30,45,60,90],"
                    + "\"timezone\":\"America/Los_Angeles\"},"
                    + META
                    + "}";

    String AVAILABILITY_SUMMARY =
            "{\"data\":{\"rules\":[{\"id\":\"a1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"dayOfWeek\":1,\"startLocal\":\"09:00\",\"endLocal\":\"17:00\","
                    + "\"timezone\":\"America/Los_Angeles\",\"effectiveFrom\":\"2026-06-01\","
                    + "\"effectiveTo\":null,\"active\":true,"
                    + "\"createdAt\":\"2026-06-01T12:00:00Z\"}],"
                    + "\"exceptions\":[{\"id\":\"e1a2c3d4-0000-4000-8000-000000000001\","
                    + "\"exceptionDate\":\"2026-07-04\",\"type\":\"UNAVAILABLE_ALL_DAY\","
                    + "\"startLocal\":null,\"endLocal\":null,\"reason\":\"Holiday\","
                    + "\"createdAt\":\"2026-06-15T12:00:00Z\"}],"
                    + "\"bookingSettings\":{\"acceptanceMode\":\"MANUAL\","
                    + "\"responseDeadlineMin\":90,\"minNoticeMin\":1440,\"maxAdvanceDays\":30,"
                    + "\"bufferBeforeMin\":0,\"bufferAfterMin\":15,"
                    + "\"durationsOffered\":[30,45,60,90],"
                    + "\"timezone\":\"America/Los_Angeles\"}},"
                    + META
                    + "}";

    String ENVELOPE_NULL = "{\"data\":null," + META + "}";
}
