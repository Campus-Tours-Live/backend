package com.CampusToursLive.domain.guide;

import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantType;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.user.RoleGrantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final UniversityRepository universities;
    private final ParticipantProfileRepository participants;
    private final UserRepository users;
    private final RoleGrantService roleGrant;
    private final ObjectMapper mapper;

    public GuideService(
            GuideProfileRepository guides,
            GuideVerificationRepository verifications,
            UniversityRepository universities,
            ParticipantProfileRepository participants,
            UserRepository users,
            RoleGrantService roleGrant,
            ObjectMapper mapper) {
        this.guides = guides;
        this.verifications = verifications;
        this.universities = universities;
        this.participants = participants;
        this.users = users;
        this.roleGrant = roleGrant;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public GuideProfileResponse getProfile(UserEntity user) {
        GuideProfileEntity profile = guides.findByUserId(user.getId()).orElse(null);
        return toResponse(user, profile);
    }

    @Transactional
    public GuideProfileResponse updateProfile(UserEntity user, GuideProfileUpdateRequest req) {
        boolean submit = Boolean.TRUE.equals(req.submit());

        // users-table fields + synced display name.
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

            // Grant the GUIDE role (user_roles); approval is tracked on the guide
            // profile's application_status, NOT on the account-wide accountStatus.
            roleGrant.grant(user, UserRole.GUIDE);
        } else {
            guides.save(profile);
        }

        users.save(user);
        return toResponse(user, profile);
    }

    private UUID parseUniversity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        UUID id;
        try {
            id = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid universityId: " + raw);
        }
        if (!universities.existsById(id)) {
            throw new ValidationException("Unknown universityId: " + raw);
        }
        return id;
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
        }
        guides.save(profile);

        UserEntity guideUser =
                users.findById(guideUserId)
                        .orElseThrow(() -> new NotFoundException("User not found"));
        return toResponse(guideUser, profile);
    }

    private GuideProfileResponse toResponse(UserEntity user, GuideProfileEntity profile) {
        return new GuideProfileResponse(
                user.getId().toString(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAccountStatus() != null ? user.getAccountStatus().name() : null,
                profile == null
                        ? null
                        : (profile.getUniversityId() != null
                                ? profile.getUniversityId().toString()
                                : null),
                profile == null ? null : profile.getMajor(),
                profile == null ? null : profile.getClassYear(),
                profile == null ? null : profile.getBio(),
                profile == null ? null : readArray(profile.getLanguages()),
                profile == null ? null : readArray(profile.getSpecialties()),
                profile == null ? null : profile.getBasePriceCents(),
                profile == null ? null : profile.getCurrency(),
                profile == null
                        ? null
                        : (profile.getApplicationStatus() != null
                                ? profile.getApplicationStatus().name()
                                : null),
                profile == null
                        ? null
                        : (profile.getVerificationStatus() != null
                                ? profile.getVerificationStatus().name()
                                : null));
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
