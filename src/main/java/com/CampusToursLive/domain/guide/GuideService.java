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
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.integration.scorecard.SchoolDirectory;
import com.CampusToursLive.security.GuideProfileSnapshot;
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
 * Guide application / onboarding. Upserts {@code guide_profiles} and its per-university {@code
 * guide_universities} row, and (on submit) grants the GUIDE role (user_roles) and sets the guide's
 * own guide_status to PENDING. Verification (the stubbed email-verify flow, tracked on
 * guide_universities.verification_status) is what later flips guide_status to VERIFIED — admin
 * review has been retired. Account-wide accountStatus is NOT touched — guide approval is a
 * role-level state, kept on the guide profile rather than the account.
 */
@Service
public class GuideService {

    private final GuideProfileRepository guides;
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
            GuideUniversityRepository guideUniversities,
            UniversityRepository universities,
            ParticipantProfileRepository participants,
            UserRepository users,
            RoleGrantService roleGrant,
            SchoolDirectory schools,
            CampusImageUrls campusImages,
            ObjectMapper mapper) {
        this.guides = guides;
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

    /**
     * {@code GET /guide/profile}'s read path: builds the response directly from the {@link
     * GuideProfileSnapshot} {@link com.CampusToursLive.security.CurrentUser#requireGuide()} already
     * resolved (account + role-profile pairing already asserted there) — no second {@code
     * guide_profiles} lookup. Only the per-university affiliations still require their own read
     * (there is no snapshot equivalent for {@code guide_universities}).
     */
    @Transactional(readOnly = true)
    public GuideProfileResponse getProfile(GuideProfileSnapshot profile) {
        return new GuideProfileResponse(
                profile.status() != null ? profile.status().name() : null,
                buildUniversityViews(profile.id()),
                profile.bio(),
                readArray(profile.spokenLanguages()),
                readArray(profile.tourTopics()));
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

        // A guide always needs a university + major (enforced here even though they now live on
        // the per-university guide_universities row, not on guide_profiles itself).
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

        if (req.bio() != null) profile.setBio(req.bio().trim());
        if (req.spokenLanguages() != null) {
            List<String> langs =
                    req.spokenLanguages().stream().filter(s -> s != null && !s.isBlank()).toList();
            profile.setSpokenLanguages(writeJson(langs.isEmpty() ? List.of("en-US") : langs));
        }
        if (req.tourTopics() != null) {
            profile.setTourTopics(writeJson(validateTopics(req.tourTopics())));
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
            if (readArray(profile.getTourTopics()).isEmpty()) {
                throw new ValidationException(
                        "At least one tour specialty is required to submit your application");
            }
            profile.setStatus(GuideStatus.PENDING);
            guides.save(profile);

            // Submission lives entirely on guide_universities (the per-university row):
            // school_email + verification_status=PENDING, keyed off the request's
            // university/major/degree/classYear.
            GuideUniversityEntity guideUniversity =
                    writeGuideUniversity(
                            profile, universityId, major, degree, req.classYear(), req.entryYear());
            guideUniversity.setSchoolEmail(email);
            guideUniversity.setVerificationStatus(GuideVerificationStatus.PENDING);
            guideUniversities.save(guideUniversity);

            // Grant the GUIDE role (user_roles); approval is tracked on the guide
            // profile's guide_status, NOT on the account-wide accountStatus.
            roleGrant.grant(user, UserRole.GUIDE);
        } else {
            guides.save(profile);
            guideUniversities.save(
                    writeGuideUniversity(
                            profile,
                            universityId,
                            major,
                            degree,
                            req.classYear(),
                            req.entryYear()));
        }

        users.save(user);
        return toResponse(profile);
    }

    /**
     * Upsert the {@code guide_universities} row for {@code universityId} (keyed by {@code
     * (guide_profile_id, university_id)}, single school today), writing
     * major/degree/classYear/entryYear directly from the request — {@code guide_profiles} no longer
     * carries these flat columns. Does NOT save — callers persist it (after possibly layering on
     * submit-only fields like {@code schoolEmail}).
     */
    private GuideUniversityEntity writeGuideUniversity(
            GuideProfileEntity profile,
            UUID universityId,
            String major,
            String degree,
            String classYear,
            Integer entryYear) {
        GuideUniversityEntity entry =
                guideUniversities.findByGuideProfileId(profile.getId()).stream()
                        .filter(g -> universityId.equals(g.getUniversityId()))
                        .findFirst()
                        .orElseGet(
                                () -> {
                                    GuideUniversityEntity g = new GuideUniversityEntity();
                                    g.setId(UUID.randomUUID());
                                    g.setGuideProfileId(profile.getId());
                                    g.setUniversityId(universityId);
                                    return g;
                                });
        entry.setMajor(major);
        entry.setDegree(degree);
        if (classYear != null) entry.setClassYear(classYear.trim());
        if (entryYear != null) entry.setEntryYear(entryYear);
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
                            // Reuse-by-name absorption: a legacy seed row (human slug) sharing this
                            // Scorecard school's exact name gets re-keyed into the sc-<id>
                            // namespace
                            // instead of creating a duplicate row. This converges the catalog to
                            // Scorecard-keyed rows over time. city/region/imageUrl are left as-is —
                            // only the slug (always) and shortName (when Scorecard has one) change.
                            UniversityEntity existing =
                                    universities.findFirstByName(s.name()).orElse(null);
                            if (existing != null) {
                                existing.setSlug(slug);
                                if (s.shortName() != null && !s.shortName().isBlank()) {
                                    existing.setShortName(s.shortName());
                                }
                                return universities.save(existing).getId();
                            }
                            UniversityEntity u = new UniversityEntity();
                            u.setId(UUID.randomUUID());
                            u.setSlug(slug);
                            u.setName(s.name());
                            u.setShortName(s.shortName());
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

    private GuideProfileResponse toResponse(GuideProfileEntity profile) {
        return new GuideProfileResponse(
                profile == null
                        ? null
                        : (profile.getStatus() != null ? profile.getStatus().name() : null),
                profile == null ? List.of() : buildUniversityViews(profile.getId()),
                profile == null ? null : profile.getBio(),
                profile == null ? null : readArray(profile.getSpokenLanguages()),
                profile == null ? null : readArray(profile.getTourTopics()));
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
                                    row.getEntryYear(),
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
