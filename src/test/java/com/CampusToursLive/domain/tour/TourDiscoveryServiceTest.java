package com.CampusToursLive.domain.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.TourDetailResponse;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/** Public marketplace discovery — only ACTIVE offerings from APPROVED guides are visible. */
@ExtendWith(MockitoExtension.class)
class TourDiscoveryServiceTest {

    @Mock TourOfferingRepository offerings;
    @Mock GuideProfileRepository guides;
    @Mock UniversityRepository universities;
    @Mock UserRepository users;

    private TourDiscoveryService service() {
        return new TourDiscoveryService(offerings, guides, universities, users, new ObjectMapper());
    }

    private static TourOfferingEntity offering(UUID id, UUID guideId, UUID universityId) {
        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(id);
        o.setGuideId(guideId);
        o.setUniversityId(universityId);
        o.setTitle("Campus walk");
        o.setSlug("campus-walk");
        o.setDescription("A great tour");
        o.setTopic(TourTopic.GENERAL_CAMPUS);
        o.setDurationMin(60);
        o.setPriceCents(4200L);
        o.setCurrency("USD");
        o.setLanguages("[\"en-US\"]");
        o.setAvgRating(new BigDecimal("4.50"));
        o.setReviewCount(12);
        return o;
    }

    @Test
    void list_mapsDiscoverableOfferings() {
        UUID oid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UUID univId = UUID.randomUUID();

        TourOfferingEntity row = offering(oid, gid, univId);
        when(offerings.findDiscoverable(eq(null), eq(null), eq(""), any(Pageable.class)))
                .thenReturn(List.of(row));

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(gid);
        guide.setUserId(uid);
        guide.setBio("Student guide");
        when(guides.findAllById(List.of(gid))).thenReturn(List.of(guide));

        UserEntity user = new UserEntity();
        user.setId(uid);
        user.setDisplayName("Maya Chen");
        when(users.findAllById(List.of(uid))).thenReturn(List.of(user));

        UniversityEntity university = new UniversityEntity();
        university.setId(univId);
        university.setName("North Coast University");
        university.setSlug("north-coast");
        university.setCity("Arcata");
        university.setRegion("CA");
        university.setStatus(UniversityStatus.ACTIVE);
        when(universities.findAllById(List.of(univId))).thenReturn(List.of(university));

        List<TourSummaryResponse> res =
                service().list(null, null, "", TourDiscoverySort.RECOMMENDED, 20);

        assertEquals(1, res.size());
        assertEquals(oid.toString(), res.get(0).id());
        assertEquals("North Coast University", res.get(0).universityName());
        assertEquals("Maya Chen", res.get(0).guideDisplayName());
        assertEquals(4200L, res.get(0).priceCents());
        assertEquals(4.5, res.get(0).avgRating());
        assertEquals(12, res.get(0).reviewCount());
    }

    @Test
    void list_clampsLimitToFifty() {
        when(offerings.findDiscoverable(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service().list(null, null, "", TourDiscoverySort.RECOMMENDED, 100);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(offerings).findDiscoverable(eq(null), eq(null), eq(""), page.capture());
        assertEquals(50, page.getValue().getPageSize());
    }

    @Test
    void list_clampsLimitToOne_whenBelowMinimum() {
        when(offerings.findDiscoverable(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service().list(null, null, "", TourDiscoverySort.RECOMMENDED, 0);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(offerings).findDiscoverable(eq(null), eq(null), eq(""), page.capture());
        assertEquals(1, page.getValue().getPageSize());
    }

    @Test
    void list_throws422_whenUniversityIdInvalid() {
        assertThrows(
                ValidationException.class,
                () -> service().list("bad-id", null, "", TourDiscoverySort.RECOMMENDED, 20));
    }

    @Test
    void list_throws422_whenTopicInvalid() {
        assertThrows(
                ValidationException.class,
                () -> service().list(null, "NOT_A_TOPIC", "", TourDiscoverySort.RECOMMENDED, 20));
    }

    @Test
    void parseSort_defaultsToRecommended_whenBlank() {
        assertEquals(TourDiscoverySort.RECOMMENDED, TourDiscoveryService.parseSort("  "));
    }

    @Test
    void parseSort_throws422_whenUnknown() {
        assertThrows(ValidationException.class, () -> TourDiscoveryService.parseSort("NEWEST"));
    }

    @Test
    void getById_returnsDetail_whenDiscoverable() {
        UUID oid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UUID univId = UUID.randomUUID();
        TourOfferingEntity row = offering(oid, gid, univId);

        when(offerings.findDiscoverableById(oid)).thenReturn(Optional.of(row));

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(gid);
        guide.setUserId(uid);
        guide.setApplicationStatus(GuideApplicationStatus.APPROVED);
        guide.setBio("Bio text");
        when(guides.findAllById(List.of(gid))).thenReturn(List.of(guide));

        UserEntity user = new UserEntity();
        user.setId(uid);
        user.setDisplayName("Maya Chen");
        when(users.findAllById(List.of(uid))).thenReturn(List.of(user));

        UniversityEntity university = new UniversityEntity();
        university.setId(univId);
        university.setName("North Coast University");
        university.setSlug("north-coast");
        university.setCity("Arcata");
        university.setRegion("CA");
        when(universities.findAllById(List.of(univId))).thenReturn(List.of(university));

        TourDetailResponse res = service().getById(oid);
        assertEquals("Campus walk", res.title());
        assertEquals("A great tour", res.description());
        assertEquals(List.of("en-US"), res.languages());
        assertEquals("north-coast", res.universitySlug());
        assertEquals("Bio text", res.guideBio());
    }

    @Test
    void getById_throws404_whenNotDiscoverable() {
        UUID oid = UUID.randomUUID();
        when(offerings.findDiscoverableById(oid)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service().getById(oid));
    }

    @Test
    void getById_throws404_whenGuideMissingFromLookup() {
        UUID oid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID univId = UUID.randomUUID();
        when(offerings.findDiscoverableById(oid))
                .thenReturn(Optional.of(offering(oid, gid, univId)));
        when(guides.findAllById(List.of(gid))).thenReturn(List.of());
        when(universities.findAllById(List.of(univId))).thenReturn(List.of());

        assertThrows(NotFoundException.class, () -> service().getById(oid));
    }
}
