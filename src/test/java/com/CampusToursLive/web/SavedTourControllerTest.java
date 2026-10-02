package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.saved.SavedTourService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.PagedResponse;
import com.CampusToursLive.web.dto.SavedTourResponse;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * SavedTourController — thin adapter: enforces PARTICIPANT role, delegates to SavedTourService,
 * wraps the result in the {@code {data, meta}} envelope.
 */
@ExtendWith(MockitoExtension.class)
class SavedTourControllerTest {

    @Mock CurrentUser currentUser;
    @Mock SavedTourService savedTourService;

    private SavedTourController controller() {
        return new SavedTourController(currentUser, savedTourService);
    }

    private UserEntity participant() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        when(currentUser.requireRole(UserRole.PARTICIPANT)).thenReturn(u);
        return u;
    }

    @Test
    void list_wrapsServicePageAsPagedResponse() {
        UserEntity u = participant();
        TourSummaryResponse card = mock(TourSummaryResponse.class);
        when(savedTourService.list(u.getId(), 2, 10))
                .thenReturn(new PageImpl<>(List.of(card), PageRequest.of(2, 10), 21));

        PagedResponse<TourSummaryResponse> data = controller().list(2, 10).data();

        assertEquals(List.of(card), data.items());
        assertEquals(2, data.page());
        assertEquals(10, data.size());
        assertEquals(21, data.totalElements());
        assertEquals(3, data.totalPages());
    }

    @Test
    void ids_returnsTheCallersSavedIds() {
        UserEntity u = participant();
        List<UUID> ids = List.of(UUID.randomUUID());
        when(savedTourService.savedTourIds(u.getId())).thenReturn(ids);

        assertSame(ids, controller().ids().data());
    }

    @Test
    void save_returnsOfferingIdAndNewlySavedFlag() {
        UserEntity u = participant();
        UUID offeringId = UUID.randomUUID();
        when(savedTourService.save(u.getId(), offeringId)).thenReturn(true);

        SavedTourResponse data = controller().save(offeringId).data();

        assertEquals(offeringId.toString(), data.tourOfferingId());
        assertTrue(data.newlySaved());
    }

    @Test
    void save_reportsNotNewlySaved_whenAlreadySaved() {
        UserEntity u = participant();
        UUID offeringId = UUID.randomUUID();
        when(savedTourService.save(u.getId(), offeringId)).thenReturn(false);

        assertFalse(controller().save(offeringId).data().newlySaved());
    }

    @Test
    void save_propagates404_whenTourNotBookable() {
        UserEntity u = participant();
        UUID offeringId = UUID.randomUUID();
        when(savedTourService.save(u.getId(), offeringId))
                .thenThrow(new NotFoundException("Tour not found"));

        assertThrows(NotFoundException.class, () -> controller().save(offeringId));
    }

    @Test
    void unsave_delegatesForTheCaller() {
        UserEntity u = participant();
        UUID offeringId = UUID.randomUUID();

        controller().unsave(offeringId);

        verify(savedTourService).unsave(u.getId(), offeringId);
    }

    @Test
    void unsave_403_whenCallerIsNotParticipant_andServiceNeverCalled() {
        when(currentUser.requireRole(UserRole.PARTICIPANT))
                .thenThrow(
                        new ForbiddenException(
                                "Missing required role: PARTICIPANT", "ROLE_REQUIRED"));

        assertThrows(ForbiddenException.class, () -> controller().unsave(UUID.randomUUID()));
        verifyNoInteractions(savedTourService);
    }

    @Test
    void list_403_whenCallerIsNotParticipant_andServiceNeverCalled() {
        when(currentUser.requireRole(UserRole.PARTICIPANT))
                .thenThrow(
                        new ForbiddenException(
                                "Missing required role: PARTICIPANT", "ROLE_REQUIRED"));

        assertThrows(ForbiddenException.class, () -> controller().list(0, 20));
        verifyNoInteractions(savedTourService);
    }
}
