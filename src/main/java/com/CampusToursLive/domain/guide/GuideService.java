package com.CampusToursLive.domain.guide;

import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.domain.university.CampusImageUrls;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.NameRules;
import com.CampusToursLive.domain.user.RoleGrantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.integration.scorecard.SchoolDirectory;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
import com.CampusToursLive.web.dto.GuideUniversityView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guide application / onboarding. Upserts {@code guide_profiles}, records a student-verification
 * submission, and (on submit) grants the GUIDE role (user_roles) and sets the guide's own
 * application_status to PENDING_REVIEW for admin review. Account-wide accountStatus is NOT touched
 * — guide approval is a role-level state, kept on the guide profile rather than the account.
 */
@Service
public class GuideService {

    private static final long MIN_PRICE_CENTS = 2000L; // $20
    private static final long MAX_PRICE_CENTS = 20000L; // $200

    private final GuideProfileRepository guides;
    private final GuideVerificationRepository verifications;
    private final GuideUniversityRepository guideUniversities;
    private final UniversityRepository universities;
    private final ParticipantProfileRepository participants;
    private final UserRepository users;
    private final RoleGrantService roleGrant;
    private final SchoolDirectory schools;
    private final CampusImageUrls campusImages;
    private final ObjectMapper mapper;

    public GuideService(
            GuideProfileRepository guides,
            GuideVerificationRepository verifications,
            GuideUniversityRepository guideUniversities,
            UniversityRepository universities,
            ParticipantProfileRepository participants,
            UserRepository users,
            RoleGrantService roleGrant,
            SchoolDirectory schools,
            CampusImageUrls campusImages,
            ObjectMapper mapper) {
        this.guides = guides;
        this.verifications = verifications;
        this.guideUniversities = guideUniversities;
        this.universities = universities;
        this.participants = participants;
        this.users = users;
        this.roleGrant = roleGrant;
        this.schools = schools;
        this.campusImages = campusImages;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public GuideProfileResponse getProfile(UserEntity user) {
        GuideProfileEntity profile = guides.findByUserId(user.getId()).orElse(null);
        return toResponse(profile);
    }

    @Transactional
    public GuideProfileResponse updateProfile(UserEntity user, GuideProfileUpdateRequest req) {
        boolean submit = Boolean.TRUE.equals(req.submit());

        // users-table fields + synced display name.
        NameRules.validate("firstName", req.firstName());
        NameRules.validate("lastName", req.lastName());
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.firstName() != null || req.lastName() != null) {
            String full =
                    (nullToEmpty(user.getFirstName()) + " " + nullToEmpty(user.getLastName()))
                            .trim();
            if (!full.isEmpty()) user.setDisplayName(full);
        }

        // guide_profiles requires university_id + major (NOT NULL), so enforce them
        // whenever we create/persist the row.
        UUID universityId = parseUniversity(req.universityId());
        String major = req.major() == null ? null : req.major().trim();
        if (universityId == null) {
            throw new ValidationException("universityId is required");
        }
        if (major == null || major.isEmpty()) {
            throw new ValidationException("major is required");
        }
        String degree = req.degree() == null ? null : req.degree().trim();
        if (degree == null || degree.isEmpty()) {
            throw new ValidationException("degree is required");
        }
        validateClassYear(req.classYear(), degree);

        GuideProfileEntity profile =
                guides.findByUserId(user.getId())
                        .orElseGet(
                                () -> {
                                    GuideProfileEntity p = new GuideProfileEntity();
                                    p.setId(UUID.randomUUID());
                                    p.setUserId(user.getId());
                                    return p;
                                });

        profile.setUniversityId(universityId);
        profile.setMajor(major);
        if (req.classYear() != null) profile.setClassYear(req.classYear().trim());
        profile.setDegree(degree);
        if (req.bio() != null) profile.setBio(req.bio().trim());
        if (req.languages() != null) {
            List<String> langs =
                    req.languages().stream().filter(s -> s != null && !s.isBlank()).toList();
            profile.setLanguages(writeJson(langs.isEmpty() ? List.of("en-US") : langs));
        }
        if (req.specialties() != null) {
            profile.setSpecialties(writeJson(validateTopics(req.specialties())));
        }
        if (req.basePriceCents() != null) {
            long price = req.basePriceCents();
            if (price < MIN_PRICE_CENTS || price > MAX_PRICE_CENTS) {
                throw new ValidationException(
                        "basePriceCents must be between "
                                + MIN_PRICE_CENTS
                                + " and "
                                + MAX_PRICE_CENTS);
            }
            profile.setBasePriceCents(price);
        }

        if (submit) {
            // Parent/guardian participants cannot become guides (bidirectional
            // exclusion). A guide-only account has no participant_profile → allowed.
            participants
                    .findByUserId(user.getId())
                    .ifPresent(
                            pp -> {
                                if (pp.getParticipantType() == ParticipantType.PARENT) {
                                    throw new ValidationException(
                                            "Parent or guardian accounts cannot become guides.");
                                }
                            });

            String email = req.verificationEmail() == null ? null : req.verificationEmail().trim();
            if (email == null || !email.contains("@")) {
                throw new ValidationException(
                        "A valid school email (verificationEmail) is required to submit your application");
            }
            // bio + at least one specialty are required to submit a complete application
            // (server-side
            // defense mirroring the client; the required university/major/degree are enforced
            // above).
            if (profile.getBio() == null || profile.getBio().isBlank()) {
                throw new ValidationException("A short bio is required to submit your application");
            }
            if (readArray(profile.getSpecialties()).isEmpty()) {
                throw new ValidationException(
                        "At least one tour specialty is required to submit your application");
            }
            profile.setApplicationStatus(GuideApplicationStatus.PENDING_REVIEW);
            profile.setVerificationStatus(GuideVerificationStatus.PENDING);
            guides.save(profile);

            GuideVerificationEntity v = new GuideVerificationEntity();
            v.setId(UUID.randomUUID());
            v.setGuideId(profile.getId());
            v.setMethod("UNIVERSITY_EMAIL");
            v.setUniversityEmail(email);
            v.setStatus(GuideVerificationStatus.PENDING);
            verifications.save(v);

            // Mirror the submission onto guide_universities (the per-university row):
            // school_email + verification_status=PENDING alongside the flat guide_profiles columns.
            GuideUniversityEntity guideUniversity = syncGuideUniversity(profile);
            guideUniversity.setSchoolEmail(email);
            guideUniversity.setVerificationStatus(GuideVerificationStatus.PENDING);
            guideUniversities.save(guideUniversity);

            // Grant the GUIDE role (user_roles); approval is tracked on the guide
            // profile's application_status, NOT on the account-wide accountStatus.
            roleGrant.grant(user, UserRole.GUIDE);
        } else {
            guides.save(profile);
            guideUniversities.save(syncGuideUniversity(profile));
        }

        users.save(user);
        return toResponse(profile);
    }

    /**
     * Upsert the {@code guide_universities} row for this profile's current university (keyed by
     * {@code (guide_profile_id, university_id)}, single school today) so major/degree/classYear
     * stay in sync with the flat {@code guide_profiles} columns. Does NOT save — callers persist it
     * (after possibly layering on submit-only fields like {@code schoolEmail}).
     */
    private GuideUniversityEntity syncGuideUniversity(GuideProfileEntity profile) {
        GuideUniversityEntity entry =
                guideUniversities.findByGuideProfileId(profile.getId()).stream()
                        .filter(g -> profile.getUniversityId().equals(g.getUniversityId()))
                        .findFirst()
                        .orElseGet(
                                () -> {
                                    GuideUniversityEntity g = new GuideUniversityEntity();
                                    g.setId(UUID.randomUUID());
                                    g.setGuideProfileId(profile.getId());
                                    g.setUniversityId(profile.getUniversityId());
                                    return g;
                                });
        entry.setMajor(profile.getMajor());
        entry.setDegree(profile.getDegree());
        entry.setClassYear(profile.getClassYear());
        return entry;
    }

    /**
     * Resolve the submitted university to a local id. A UUID must be an existing local university
     * (the seeded catalog or a previously-upserted school). Anything else is treated as a live
     * College Scorecard school id and upserted into the catalog on first use — so onboarding can
     * offer every U.S. school without pre-seeding them.
     */
    private UUID parseUniversity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        try {
            UUID id = UUID.fromString(value);
            if (universities.existsById(id)) return id;
            throw new ValidationException("Unknown universityId: " + raw);
        } catch (IllegalArgumentException notUuid) {
            return upsertFromDirectory(value);
        }
    }

    /** Idempotently persist a live-directory school (keyed by a stable {@code sc-<id>} slug). */
    private UUID upsertFromDirectory(String scorecardId) {
        String slug = "sc-" + scorecardId;
        return universities
                .findBySlug(slug)
                .map(UniversityEntity::getId)
                .orElseGet(
                        () -> {
                            SchoolDirectory.SchoolRef s = schools.getSchool(scorecardId);
                            if (s == null) {
                                throw new ValidationException("Unknown university: " + scorecardId);
                            }
                            UniversityEntity u = new UniversityEntity();
                            u.setId(UUID.randomUUID());
                            u.setSlug(slug);
                            u.setName(s.name());
                            u.setCity(s.city() == null || s.city().isBlank() ? "N/A" : s.city());
                            u.setRegion(s.state());
                            u.setTimezone(tzForState(s.state()));
                            u.setStatus(UniversityStatus.ACTIVE);
                            u.setImageUrl(campusImages.forName(s.name()));
                            return universities.save(u).getId();
                        });
    }

    /** Test-only shim: exposes the private upsert path for {@code GuideServiceTest}. */
    UUID resolveUniversityForTest(String scorecardId) {
        return upsertFromDirectory(scorecardId);
    }

    /** Best-effort IANA zone for a US state code (demo-grade; onboarding can refine later). */
    private static String tzForState(String state) {
        if (state == null) return "America/New_York";
        return switch (state.trim().toUpperCase()) {
            case "CA", "WA", "OR", "NV" -> "America/Los_Angeles";
            case "AZ" -> "America/Phoenix";
            case "CO", "UT", "NM", "MT", "WY", "ID" -> "America/Denver";
            case "TX",
                            "IL",
                            "MO",
                            "MN",
                            "WI",
                            "IA",
                            "LA",
                            "AR",
                            "OK",
                            "KS",
                            "NE",
                            "SD",
                            "ND",
                            "AL",
                            "MS",
                            "TN" ->
                    "America/Chicago";
            case "HI" -> "Pacific/Honolulu";
            case "AK" -> "America/Anchorage";
            default -> "America/New_York";
        };
    }

    private List<String> validateTopics(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String t : raw) {
            if (t == null || t.isBlank()) continue;
            try {
                out.add(TourTopic.valueOf(t).name());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Invalid specialty topic: " + t);
            }
        }
        return out;
    }

    /**
     * Class year (expected graduation year) must, when present, be a 4-digit year inside a bounded
     * window: 10 years back (recent alumni can guide) up to this year plus a per-degree buffer (a
     * current student's remaining program length). Mirrors the client-side rule — defense in depth,
     * since a direct API call bypasses the browser. Year granularity keeps term/quarter timing from
     * ever making a year invalid.
     */
    private static void validateClassYear(String classYear, String degree) {
        if (classYear == null || classYear.isBlank()) return;
        String cy = classYear.trim();
        if (!cy.matches("\\d{4}")) {
            throw new ValidationException("classYear must be a 4-digit year");
        }
        int year = Integer.parseInt(cy);
        int current = Year.now().getValue();
        int min = current - 10;
        int max = current + gradYearBufferForDegree(degree);
        if (year < min || year > max) {
            throw new ValidationException("classYear must be between " + min + " and " + max);
        }
    }

    /**
     * Upper-bound buffer (years past this year) for an expected graduation year, by degree level.
     * Package-private so each per-level branch is unit-tested directly (see GuideServiceTest).
     */
    static int gradYearBufferForDegree(String degree) {
        String t = degree == null ? "" : degree.toLowerCase();
        if (t.contains("doctor") || t.contains("first professional")) return 9;
        if (t.contains("master") || t.contains("post-baccalaureate")) return 3;
        if (t.contains("bachelor")) return 6;
        if (t.contains("associate") || t.contains("certificate") || t.contains("diploma")) return 3;
        return 8;
    }

    /**
     * Admin review of a guide application (called by AdminController after requireRole(ADMIN)).
     * Sets the guide's application_status; approving also marks the verification VERIFIED. This is
     * what makes APPROVED reachable, so the live-action gate on offerings
     * (TourOfferingService.activate) can pass.
     */
    @Transactional
    public GuideProfileResponse reviewApplication(UUID guideUserId, String decision) {
        String d = decision == null ? null : decision.trim().toUpperCase();
        GuideApplicationStatus next;
        if ("APPROVED".equals(d)) {
            next = GuideApplicationStatus.APPROVED;
        } else if ("REJECTED".equals(d)) {
            next = GuideApplicationStatus.REJECTED;
        } else {
            throw new ValidationException("decision must be APPROVED or REJECTED");
        }

        GuideProfileEntity profile =
                guides.findByUserId(guideUserId)
                        .orElseThrow(
                                () -> new NotFoundException("No guide application for that user"));
        profile.setApplicationStatus(next);
        if (next == GuideApplicationStatus.APPROVED) {
            profile.setVerificationStatus(GuideVerificationStatus.VERIFIED);
            // Mirror onto guide_universities: the response's per-school verificationStatus is
            // read from these rows (not the flat profile column), so an approve must flip both
            // or the guide's response would still show a stale PENDING/NOT_SUBMITTED status.
            for (GuideUniversityEntity row :
                    guideUniversities.findByGuideProfileId(profile.getId())) {
                row.setVerificationStatus(GuideVerificationStatus.VERIFIED);
                guideUniversities.save(row);
            }
        }
        guides.save(profile);

        // Data-integrity guard: the guide_profile row references a user that must still exist.
        users.findById(guideUserId).orElseThrow(() -> new NotFoundException("User not found"));
        return toResponse(profile);
    }

    private GuideProfileResponse toResponse(GuideProfileEntity profile) {
        return new GuideProfileResponse(
                profile == null
                        ? null
                        : (profile.getApplicationStatus() != null
                                ? profile.getApplicationStatus().name()
                                : null),
                profile == null ? List.of() : buildUniversityViews(profile.getId()),
                profile == null ? null : profile.getBio(),
                profile == null ? null : readArray(profile.getLanguages()),
                profile == null ? null : readArray(profile.getSpecialties()),
                profile == null ? null : profile.getBasePriceCents(),
                profile == null ? null : profile.getCurrency());
    }

    /**
     * One {@link GuideUniversityView} per {@code guide_universities} row for this profile,
     * resolving each row's university name/shortName. {@code schoolEmail} (PII) is intentionally
     * never read into the view.
     */
    private List<GuideUniversityView> buildUniversityViews(UUID profileId) {
        return guideUniversities.findByGuideProfileId(profileId).stream()
                .map(
                        row -> {
                            UniversityEntity uni =
                                    row.getUniversityId() != null
                                            ? universities
                                                    .findById(row.getUniversityId())
                                                    .orElse(null)
                                            : null;
                            return new GuideUniversityView(
                                    row.getUniversityId() != null
                                            ? row.getUniversityId().toString()
                                            : null,
                                    uni != null ? uni.getName() : null,
                                    uni != null ? uni.getShortName() : null,
                                    row.getMajor(),
                                    row.getDegree(),
                                    row.getClassYear(),
                                    row.getVerificationStatus() != null
                                            ? row.getVerificationStatus().name()
                                            : null);
                        })
                .toList();
    }

    private List<String> readArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
