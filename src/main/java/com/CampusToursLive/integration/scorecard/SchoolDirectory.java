package com.CampusToursLive.integration.scorecard;

import com.CampusToursLive.web.MetaController.Option;
import java.util.List;

/**
 * Live directory of U.S. schools + their programs, backing the guide-onboarding dropdowns. Real
 * onboarding is not a fixed seed — it searches every U.S. institution and reads that school's
 * actual fields of study. Implemented over the College Scorecard API ({@link ScorecardClient}); an
 * interface so controllers/tests don't depend on the HTTP client.
 */
public interface SchoolDirectory {

    /**
     * Search schools by (partial) name → { value = school id, label = "Name — City, ST" }. {@code
     * page} is zero-based and maps to College Scorecard's {@code page} query param.
     */
    List<Option> searchSchools(String query, int limit, int page);

    /** The distinct majors (CIP-4 program titles) a school offers → { value = label = title }. */
    List<Option> majorsForSchool(String schoolId);

    /**
     * The distinct degree levels a school awards, derived from its CIP-4 programs' credential
     * titles → { value = label = credential title, e.g. "Bachelor's Degree" }, ordered lowest →
     * highest.
     */
    List<Option> degreesForSchool(String schoolId);

    /** Fetch one school's identity by id (for upsert on guide onboarding); null if not found. */
    SchoolRef getSchool(String schoolId);

    /** A school's core identity from the directory. */
    record SchoolRef(String id, String name, String shortName, String city, String state) {}
}
