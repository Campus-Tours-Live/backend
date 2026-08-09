package com.CampusToursLive.domain.user;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-snapshot projection of a {@code users} row joined against {@code user_roles}, {@code
 * guide_profiles} and {@code participant_profiles} — see {@code
 * UserRepository#findAccountProjectionByOidcSubject}. Backs {@code
 * com.CampusToursLive.security.AccountResolver}, which classifies an authenticated identity WITHOUT
 * ever loading a managed {@link UserEntity}.
 *
 * <p>Getter names must match the query's column aliases EXACTLY — Spring Data's native-query
 * interface projection wires each result column to the getter whose JavaBean property name equals
 * the column alias (see the {@code @Query} in {@link UserRepository}).
 */
public interface AccountProjection {

    UUID getId();

    String getOidcSubject();

    String getEmail();

    String getFirstName();

    String getLastName();

    String getDisplayName();

    /**
     * {@code account_status} as text (e.g. {@code "ACTIVE"}) — parse via {@link
     * AccountStatus#valueOf}.
     */
    String getAccountStatus();

    /** {@code age_band} as text, or {@code null}; parse via {@link AgeBand#valueOf}. */
    String getAgeBand();

    Instant getCreatedAt();

    /** {@code null} unless the account has been soft-deleted. */
    Instant getDeletedAt();

    boolean getGuideRole();

    boolean getParticipantRole();

    boolean getAdminRole();

    boolean getSupportRole();

    /**
     * {@code COUNT(DISTINCT guide_profiles.id)} — must be exactly 1 when {@code guideRole} holds.
     */
    long getGuideProfileCount();

    /**
     * {@code COUNT(DISTINCT participant_profiles.id)} — must be exactly 1 when {@code
     * participantRole} holds.
     */
    long getParticipantProfileCount();

    /**
     * {@code COUNT(DISTINCT participant_profiles.participant_type)} — a duplicated/corrupt profile
     * row is detectable as {@code != participantProfileCount}, independent of {@code
     * participantProfileCount} itself.
     */
    long getParticipantTypeCount();

    /**
     * {@code participant_type} as text, meaningful only when {@code participantProfileCount == 1}.
     */
    String getParticipantType();
}
