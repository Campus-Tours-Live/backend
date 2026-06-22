package com.CampusToursLive.domain.participant;

import com.CampusToursLive.domain.user.RoleGrantService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.ParticipantProfileUpdateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipantService {

    private final ParticipantProfileRepository profiles;
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final RoleGrantService roleGrant;
    private final ObjectMapper mapper;

    public ParticipantService(
            ParticipantProfileRepository profiles,
            UserRepository users,
            UserRoleRepository userRoles,
            RoleGrantService roleGrant,
            ObjectMapper mapper) {
        this.profiles = profiles;
        this.users = users;
        this.userRoles = userRoles;
        this.roleGrant = roleGrant;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ParticipantProfileResponse getProfile(UserEntity user) {
        ParticipantProfileEntity profile = profiles.findByUserId(user.getId()).orElse(null);
        return toResponse(user, profile);
    }

    @Transactional
    public ParticipantProfileResponse updateProfile(
            UserEntity user, ParticipantProfileUpdateRequest req) {
        // users-table fields
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        // Keep display_name (canonical display string) synced from first/last.
        if (req.firstName() != null || req.lastName() != null) {
            String full =
                    ((nullToEmpty(user.getFirstName()) + " " + nullToEmpty(user.getLastName()))
                            .trim());
            if (!full.isEmpty()) user.setDisplayName(full);
        }
        // Explicit displayName still wins if provided.
        if (req.displayName() != null) user.setDisplayName(req.displayName());
        if (req.preferredLanguage() != null) user.setPreferredLanguage(req.preferredLanguage());
        if (req.timezone() != null) user.setTimezone(req.timezone());

        // participant_profiles (create if missing — normally created at provisioning)
        ParticipantProfileEntity profile =
                profiles.findByUserId(user.getId())
                        .orElseGet(
                                () -> {
                                    ParticipantProfileEntity p = new ParticipantProfileEntity();
                                    p.setId(UUID.randomUUID());
                                    p.setUserId(user.getId());
                                    p.setParticipantType(ParticipantType.PROSPECTIVE);
                                    p.setInterests("{}");
                                    return p;
                                });

        if (req.participantType() != null) {
            profile.setParticipantType(parseType(req.participantType()));
        }
        // Bidirectional exclusion: a guide cannot also be a parent/guardian participant.
        if (profile.getParticipantType() == ParticipantType.PARENT
                && userRoles.existsByUserIdAndRole(user.getId(), UserRole.GUIDE)) {
            throw new ValidationException("Guides cannot also be parent or guardian participants.");
        }
        if (req.gradeLevel() != null) profile.setGradeLevel(req.gradeLevel());
        if (req.intendedMajor() != null) profile.setIntendedMajor(req.intendedMajor());

        // interests JSON: { topics, universities, accessibility }
        Map<String, Object> interests = readInterests(profile.getInterests());
        if (req.topicsOfInterest() != null) interests.put("topics", req.topicsOfInterest());
        if (req.universitiesOfInterest() != null)
            interests.put("universities", req.universitiesOfInterest());
        if (req.accessibilityPreferences() != null)
            interests.put("accessibility", req.accessibilityPreferences());
        profile.setInterests(writeJson(interests));

        profiles.save(profile);

        // Onboarding complete → grant the PARTICIPANT role (idempotent; re-edits
        // won't re-grant or change the active role).
        roleGrant.grant(user, UserRole.PARTICIPANT);
        users.save(user); // persist user-field updates + the grant's role change, once
        return toResponse(user, profile);
    }

    private ParticipantType parseType(String raw) {
        try {
            return ParticipantType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "Invalid participantType: "
                            + raw
                            + " (allowed: HIGH_SCHOOL, PROSPECTIVE, TRANSFER, INTERNATIONAL, PARENT, OTHER)");
        }
    }

    private ParticipantProfileResponse toResponse(
            UserEntity user, ParticipantProfileEntity profile) {
        Map<String, Object> interests =
                profile != null ? readInterests(profile.getInterests()) : null;
        return new ParticipantProfileResponse(
                user.getId().toString(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPreferredLanguage(),
                user.getTimezone(),
                profile == null
                        ? null
                        : (profile.getParticipantType() != null
                                ? profile.getParticipantType().name()
                                : null),
                profile == null ? null : profile.getGradeLevel(),
                profile == null ? null : profile.getIntendedMajor(),
                profile == null ? null : profile.isGuardianRequired(),
                interests == null ? null : interests.getOrDefault("topics", new ArrayList<>()),
                interests == null
                        ? null
                        : interests.getOrDefault("universities", new ArrayList<>()),
                interests == null ? null : interests.get("accessibility"));
    }

    /**
     * Tolerant read of the interests blob: accepts a JSON object, or a bare array of topic tags.
     */
    private Map<String, Object> readInterests(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                List<String> topics =
                        mapper.readValue(trimmed, new TypeReference<List<String>>() {});
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("topics", topics);
                return m;
            }
            return mapper.readValue(trimmed, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
