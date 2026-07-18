package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.web.dto.UniversityResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Plain unit test of the only logic in UniversityController: limit/page clamping, query trim, and
 * entity→response mapping. No Spring context needed — the controller is a thin holder over the
 * repository.
 */
@ExtendWith(MockitoExtension.class)
class UniversityControllerTest {

    @Mock UniversityRepository universities;

    private UniversityController controller() {
        return new UniversityController(universities);
    }

    private static UniversityEntity uni(String name) {
        UniversityEntity u = new UniversityEntity();
        u.setId(UUID.randomUUID());
        u.setSlug("slug");
        u.setName(name);
        u.setCity("City");
        u.setStatus(UniversityStatus.ACTIVE);
        return u;
    }

    private Pageable capturedPage(int requestedLimit, int requestedPage) {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        when(universities.search(eq(""), page.capture())).thenReturn(List.of());
        controller().list("", requestedLimit, requestedPage);
        return page.getValue();
    }

    @Test
    void list_clampsLimitToOne_whenBelowMinimum() {
        assertEquals(1, capturedPage(0, 0).getPageSize());
        assertEquals(1, capturedPage(-5, 0).getPageSize());
    }

    @Test
    void list_clampsLimitToFifty_whenAboveMaximum() {
        assertEquals(50, capturedPage(100, 0).getPageSize());
    }

    @Test
    void list_usesLimitAsIs_whenInRange() {
        assertEquals(20, capturedPage(20, 0).getPageSize());
    }

    @Test
    void list_usesRequestedPage_andClampsNegativePageToZero() {
        assertEquals(3, capturedPage(8, 3).getPageNumber());
        assertEquals(0, capturedPage(8, -1).getPageNumber());
    }

    @Test
    void list_trimsQuery() {
        ArgumentCaptor<String> q = ArgumentCaptor.forClass(String.class);
        when(universities.search(q.capture(), any())).thenReturn(List.of());
        controller().list("  mit  ", 20, 0);
        assertEquals("mit", q.getValue());
    }

    @Test
    void list_mapsEntitiesToResponses() {
        when(universities.search(any(), any())).thenReturn(List.of(uni("MIT")));
        List<UniversityResponse> items = controller().list("", 20, 0).data();
        assertEquals(1, items.size());
        assertEquals("MIT", items.get(0).name());
    }
}
