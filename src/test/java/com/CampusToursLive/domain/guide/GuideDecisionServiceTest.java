package com.CampusToursLive.domain.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.audit.AuditWriter;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuideDecisionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock GuideProfileRepository guides;
    @Mock GuideUniversityRepository guideUniversities;
    @Mock AuditWriter auditWriter;

    private final UUID adminId = UUID.randomUUID();
    private final UUID guideUserId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();

    private GuideDecisionService service() {
        return new GuideDecisionService(
                guides, guideUniversities, auditWriter, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void stubProfile(GuideStatus status) {
        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setId(profileId);
        profile.setUserId(guideUserId);
        profile.setStatus(status);
        when(guides.findByUserId(guideUserId)).thenReturn(Optional.of(profile));
    }

    @Test
    void approve_verifiesGuideAndUniversity_andAudits() {
        stubProfile(GuideStatus.PENDING);
        when(guides.approveIfPending(profileId, NOW)).thenReturn(1);
        when(guideUniversities.verifyPending(profileId, NOW)).thenReturn(1);

        GuideStatus result = service().decide(adminId, guideUserId, GuideDecision.APPROVE, null);

        assertThat(result).isEqualTo(GuideStatus.VERIFIED);
        verify(guideUniversities).verifyPending(profileId, NOW);
        verify(guideUniversities, never()).rejectPending(any());
        verify(auditWriter)
                .record(
                        GuideDecisionService.ACTION_GUIDE_APPLICATION_DECIDED,
                        GuideDecisionService.TARGET_TYPE_GUIDE_PROFILE,
                        profileId.toString(),
                        adminId,
                        Map.of("decision", "APPROVE"));
    }

    @Test
    void approve_withReason_keepsTrimmedReasonInAudit() {
        stubProfile(GuideStatus.PENDING);
        when(guides.approveIfPending(profileId, NOW)).thenReturn(1);
        when(guideUniversities.verifyPending(profileId, NOW)).thenReturn(1);

        service().decide(adminId, guideUserId, GuideDecision.APPROVE, "  docs checked  ");

        verify(auditWriter)
                .record(
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(adminId),
                        eq(Map.of("decision", "APPROVE", "reason", "docs checked")));
    }

    @Test
    void reject_marksGuideAndUniversityRejected_andAuditsReason() {
        stubProfile(GuideStatus.PENDING);
        when(guides.rejectIfPending(profileId)).thenReturn(1);
        when(guideUniversities.rejectPending(profileId)).thenReturn(1);

        GuideStatus result =
                service().decide(adminId, guideUserId, GuideDecision.REJECT, " fake email ");

        assertThat(result).isEqualTo(GuideStatus.REJECTED);
        verify(guideUniversities).rejectPending(profileId);
        verify(guideUniversities, never()).verifyPending(any(), any());
        verify(auditWriter)
                .record(
                        anyString(),
                        anyString(),
                        eq(profileId.toString()),
                        eq(adminId),
                        eq(Map.of("decision", "REJECT", "reason", "fake email")));
    }

    @Test
    void reject_withoutReason_isValidationError() {
        assertThatThrownBy(() -> service().decide(adminId, guideUserId, GuideDecision.REJECT, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service().decide(adminId, guideUserId, GuideDecision.REJECT, "  "))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(guides, guideUniversities, auditWriter);
    }

    @Test
    void unknownGuide_isNotFound() {
        when(guides.findByUserId(guideUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service().decide(adminId, guideUserId, GuideDecision.APPROVE, null))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(guideUniversities, auditWriter);
    }

    @Test
    void notPending_isConflictWithFreshStatus_andChangesNothing() {
        stubProfile(GuideStatus.PENDING);
        when(guides.approveIfPending(profileId, NOW)).thenReturn(0);
        GuideProfileEntity decided = new GuideProfileEntity();
        decided.setStatus(GuideStatus.REJECTED);
        when(guides.findById(profileId)).thenReturn(Optional.of(decided));

        assertThatThrownBy(
                        () -> service().decide(adminId, guideUserId, GuideDecision.APPROVE, null))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo("GUIDE_APPLICATION_NOT_PENDING");
                            assertThat(e.properties()).containsEntry("currentStatus", "REJECTED");
                        });
        verifyNoInteractions(guideUniversities);
        verify(auditWriter, never()).record(any(), any(), any(), any(), anyMap());
    }

    @Test
    void notPending_rowGone_fallsBackToLoadedStatus() {
        stubProfile(GuideStatus.VERIFIED);
        when(guides.rejectIfPending(profileId)).thenReturn(0);
        when(guides.findById(profileId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service().decide(adminId, guideUserId, GuideDecision.REJECT, "late"))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        e -> assertThat(e.properties()).containsEntry("currentStatus", "VERIFIED"));
        verifyNoInteractions(guideUniversities);
    }

    @Test
    void selfDecision_isConflict() {
        assertThatThrownBy(() -> service().decide(adminId, adminId, GuideDecision.APPROVE, null))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("GUIDE_SELF_DECISION"));
        verifyNoInteractions(guides, guideUniversities, auditWriter);
    }

    @Test
    void approve_withNoPendingUniversity_isConflict() {
        stubProfile(GuideStatus.PENDING);
        when(guides.approveIfPending(profileId, NOW)).thenReturn(1);
        when(guideUniversities.verifyPending(profileId, NOW)).thenReturn(0);

        assertThatThrownBy(
                        () -> service().decide(adminId, guideUserId, GuideDecision.APPROVE, null))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("GUIDE_UNIVERSITY_NOT_PENDING"));
        verify(auditWriter, never()).record(any(), any(), any(), any(), anyMap());
    }
}
