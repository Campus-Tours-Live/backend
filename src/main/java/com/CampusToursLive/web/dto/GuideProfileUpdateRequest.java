package com.CampusToursLive.web.dto;

import java.util.List;

/**
 * PATCH /guide/profile body — guide application / onboarding.
 *
 * <p>Maps to: users (firstName, lastName, displayName) + guide_profiles (universityId, major,
 * classYear, bio, languages, specialties, basePriceCents) + guide_verifications (method
 * UNIVERSITY_EMAIL, verificationEmail).
 *
 * <p>When {@code submit} is true the application is finalized: required fields (university, major,
 * verification email) are enforced, a verification row is created, the GUIDE role is granted
 * (user_roles) and the guide's own application_status moves to PENDING_REVIEW. The account-level
 * status is unchanged — role lifecycle lives on the profile, not the account.
 */
public record GuideProfileUpdateRequest(
        String firstName,
        String lastName,
        String universityId,
        String major,
        String classYear,
        String bio,
        List<String> languages,
        List<String> specialties,
        Long basePriceCents,
        String verificationEmail,
        Boolean submit) {}
