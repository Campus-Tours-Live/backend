package com.CampusToursLive.domain.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
        o.setFeatures("[\"Q_AND_A\"]");
        o.setCreatedAt(Instant.now());
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
        when(offerings.findDiscoverable(
                        eq(null), eq(false), anyList(), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(gid);
        guide.setUserId(uid);
        guide.setBio("Student guide");
        guide.setMajor("Computer Science");
        guide.setDegree("BS");
        guide.setEntryYear(2023);
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
        university.setImageUrl("https://r2.example/Stanford%20University.png");
        when(universities.findAllById(List.of(univId))).thenReturn(List.of(university));

        Page<TourSummaryResponse> res =
                service().list(null, null, "", TourDiscoverySort.RECOMMENDED, 0, 20);

        assertEquals(1, res.getTotalElements());
        assertEquals(1, res.getContent().size());
        assertEquals(oid.toString(), res.getContent().get(0).id());
        assertEquals("North Coast University", res.getContent().get(0).universityName());
        assertEquals(
                "https://r2.example/Stanford%20University.png",
                res.getContent().get(0).universityImageUrl());
        assertEquals("Maya Chen", res.getContent().get(0).guideDisplayName());
        assertEquals("Computer Science", res.getContent().get(0).guideMajor());
        assertEquals("BS", res.getContent().get(0).guideDegree());
        assertEquals(2023, res.getContent().get(0).guideEntryYear());
        assertEquals(List.of("en-US"), res.getContent().get(0).languages());
        assertEquals(List.of("Q_AND_A"), res.getContent().get(0).features());
        assertEquals(true, res.getContent().get(0).isNew());
        assertEquals(4200L, res.getContent().get(0).priceCents());
        assertEquals(4.5, res.getContent().get(0).avgRating());
        assertEquals(12, res.getContent().get(0).reviewCount());
    }

    @Test
    void list_clampsLimitToFifty() {
        when(offerings.findDiscoverable(any(), anyBoolean(), anyList(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service().list(null, null, "", TourDiscoverySort.RECOMMENDED, 0, 100);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(offerings).findDiscoverable(eq(null), eq(false), anyList(), eq(""), page.capture());
        assertEquals(50, page.getValue().getPageSize());
    }

    @Test
    void list_clampsLimitToOne_whenBelowMinimum() {
        when(offerings.findDiscoverable(any(), anyBoolean(), anyList(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service().list(null, null, "", TourDiscoverySort.RECOMMENDED, 0, 0);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(offerings).findDiscoverable(eq(null), eq(false), anyList(), eq(""), page.capture());
        assertEquals(1, page.getValue().getPageSize());
    }

    @Test
    void list_throws422_whenUniversityIdInvalid() {
        assertThrows(
                ValidationException.class,
                () -> service().list("bad-id", null, "", TourDiscoverySort.RECOMMENDED, 0, 20));
    }

    @Test
    void list_throws422_whenTopicInvalid() {
        assertThrows(
                ValidationException.class,
                () ->
                        service()
                                .list(
                                        null,
                                        List.of("NOT_A_TOPIC"),
                                        "",
                                        TourDiscoverySort.RECOMMENDED,
                                        0,
                                        20));
    }

    @Test
    void list_singleTopic_filtersByThatTopic() {
        when(offerings.findDiscoverable(
                        eq(null),
                        eq(true),
                        eq(List.of(TourTopic.DORM_HOUSING)),
                        eq(""),
                        any(Pageable.class)))
                .thenReturn(Page.empty());

        service().list(null, List.of("DORM_HOUSING"), "", TourDiscoverySort.RECOMMENDED, 0, 20);

        verify(offerings)
                .findDiscoverable(
                        eq(null),
                        eq(true),
                        eq(List.of(TourTopic.DORM_HOUSING)),
                        eq(""),
                        any(Pageable.class));
    }

    @Test
    void escapeLike_escapesWildcards() {
        assertEquals("!%!_!!", TourDiscoveryService.escapeLike("%_!"));
        assertEquals("plain text", TourDiscoveryService.escapeLike("plain text"));
    }

    @Test
    void list_sortsByPriceAscending() {
        assertFirstSortOrder(TourDiscoverySort.PRICE_ASC, "priceCents", Sort.Direction.ASC);
    }

    @Test
    void list_sortsByPriceDescending() {
        assertFirstSortOrder(TourDiscoverySort.PRICE_DESC, "priceCents", Sort.Direction.DESC);
    }

    @Test
    void list_sortsByRating() {
        assertFirstSortOrder(TourDiscoverySort.RATING, "avgRating", Sort.Direction.DESC);
    }

    private void assertFirstSortOrder(
            TourDiscoverySort sort, String property, Sort.Direction direction) {
        when(offerings.findDiscoverable(any(), anyBoolean(), anyList(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service().list(null, null, "", sort, 0, 20);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(offerings).findDiscoverable(eq(null), eq(false), anyList(), eq(""), page.capture());
        Sort.Order first = page.getValue().getSort().iterator().next();
        assertEquals(property, first.getProperty());
        assertEquals(direction, first.getDirection());
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
        university.setImageUrl("https://r2.example/Stanford%20University.png");
        when(universities.findAllById(List.of(univId))).thenReturn(List.of(university));

        TourDetailResponse res = service().getById(oid);
        assertEquals("Campus walk", res.title());
        assertEquals("A great tour", res.description());
        assertEquals(List.of("en-US"), res.languages());
        assertEquals(List.of("Q_AND_A"), res.features());
        assertEquals("north-coast", res.universitySlug());
        assertEquals("Bio text", res.guideBio());
        assertEquals("https://r2.example/Stanford%20University.png", res.universityImageUrl());
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

    // ---- parse edge cases ----

    @Test
    void list_parsesValidUniversityId_andTreatsNullQueryAsEmpty() {
        UUID univId = UUID.randomUUID();
        when(offerings.findDiscoverable(
                        eq(univId), eq(false), anyList(), eq(""), any(Pageable.class)))
                .thenReturn(Page.empty());

        service().list(univId.toString(), null, null, TourDiscoverySort.RECOMMENDED, 0, 20);

        verify(offerings)
                .findDiscoverable(eq(univId), eq(false), anyList(), eq(""), any(Pageable.class));
    }

    @Test
    void list_treatsBlankUniversityIdAndTopicAsAbsent() {
        when(offerings.findDiscoverable(
                        eq(null), eq(false), anyList(), eq(""), any(Pageable.class)))
                .thenReturn(Page.empty());

        service().list("   ", List.of("   "), "", TourDiscoverySort.RECOMMENDED, 0, 20);

        verify(offerings)
                .findDiscoverable(eq(null), eq(false), anyList(), eq(""), any(Pageable.class));
    }

    @Test
    void parseSort_defaultsToRecommended_whenNull() {
        assertEquals(TourDiscoverySort.RECOMMENDED, TourDiscoveryService.parseSort(null));
    }

    @Test
    void parseSort_parsesValidValueCaseInsensitively() {
        assertEquals(TourDiscoverySort.PRICE_ASC, TourDiscoveryService.parseSort("price_asc"));
    }

    // ---- parseTopics ----

    @Test
    void parseTopics_mergesRepeatedAndCommaTokensDedupedInOrder() {
        // Repeated params, each possibly comma-joined, merge into one deduped set.
        assertThat(
                        TourDiscoveryService.parseTopics(
                                List.of("GENERAL_CAMPUS,DORM_HOUSING", "DORM_HOUSING")))
                .containsExactly(TourTopic.GENERAL_CAMPUS, TourTopic.DORM_HOUSING);
    }

    @Test
    void parseTopics_trimsAndDropsEmptyTokensWithoutError() {
        assertThat(TourDiscoveryService.parseTopics(List.of(" GENERAL_CAMPUS , ", "", "  ")))
                .containsExactly(TourTopic.GENERAL_CAMPUS);
    }

    @Test
    void parseTopics_nullOrAllEmptyMeansNoFilter() {
        assertThat(TourDiscoveryService.parseTopics(null)).isEmpty();
        assertThat(TourDiscoveryService.parseTopics(List.of("", " "))).isEmpty();
    }

    @Test
    void parseTopics_fullSetMeansNoFilter() {
        List<String> all = Arrays.stream(TourTopic.values()).map(Enum::name).toList();
        assertThat(TourDiscoveryService.parseTopics(all)).isEmpty();
    }

    @Test
    void parseTopics_isCaseSensitiveExactMatch() {
        // Lowercase is NOT accepted (exact enum-name match).
        assertThatThrownBy(() -> TourDiscoveryService.parseTopics(List.of("general_campus")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parseTopics_unknownNonEmptyTokenThrows() {
        assertThatThrownBy(() -> TourDiscoveryService.parseTopics(List.of("NOT_A_TOPIC")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parseTopics_nullElementInListIsSkipped() {
        // Repeated params can arrive with a null element; it must be skipped, not NPE.
        assertThat(TourDiscoveryService.parseTopics(Arrays.asList(null, "GENERAL_CAMPUS")))
                .containsExactly(TourTopic.GENERAL_CAMPUS);
    }

    @Test
    void parseTopics_fullSetWithDuplicatesStillMeansNoFilter() {
        List<String> all =
                new ArrayList<>(Arrays.stream(TourTopic.values()).map(Enum::name).toList());
        all.add("GENERAL_CAMPUS"); // duplicate — deduped set still equals the full enum
        assertThat(TourDiscoveryService.parseTopics(all)).isEmpty();
    }

    @Test
    void parseTopics_unknownAfterValidStillThrows() {
        assertThatThrownBy(
                        () -> TourDiscoveryService.parseTopics(List.of("GENERAL_CAMPUS", "NOPE")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parseTopics_fullSetMissingOneStillFilters() {
        List<String> sevenOfEight =
                Arrays.stream(TourTopic.values()).map(Enum::name).skip(1).toList();
        assertThat(TourDiscoveryService.parseTopics(sevenOfEight))
                .hasSize(TourTopic.values().length - 1);
    }

    // ---- list(...) multi-topic wiring ----

    @Test
    void list_noTopicParam_doesNotFilterByTopic() {
        when(offerings.findDiscoverable(
                        isNull(), eq(false), anyList(), eq(""), any(Pageable.class)))
                .thenReturn(Page.empty());
        service().list(null, null, "", TourDiscoverySort.RECOMMENDED, 0, 20);
        verify(offerings)
                .findDiscoverable(isNull(), eq(false), anyList(), eq(""), any(Pageable.class));
    }

    @Test
    void list_topicSubset_filtersByThoseTopics() {
        when(offerings.findDiscoverable(
                        isNull(),
                        eq(true),
                        eq(List.of(TourTopic.GENERAL_CAMPUS, TourTopic.DORM_HOUSING)),
                        eq(""),
                        any(Pageable.class)))
                .thenReturn(Page.empty());
        service()
                .list(
                        null,
                        List.of("GENERAL_CAMPUS", "DORM_HOUSING"),
                        "",
                        TourDiscoverySort.RECOMMENDED,
                        0,
                        20);
        verify(offerings)
                .findDiscoverable(
                        isNull(),
                        eq(true),
                        eq(List.of(TourTopic.GENERAL_CAMPUS, TourTopic.DORM_HOUSING)),
                        eq(""),
                        any(Pageable.class));
    }

    // ---- toSummary / toDetail fallbacks (null topic, missing guide user) ----

    @Test
    void list_nullTopic_andMissingGuideUser_useFallbacks() {
        UUID oid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UUID univId = UUID.randomUUID();
        TourOfferingEntity row = offering(oid, gid, univId);
        row.setTopic(null);
        when(offerings.findDiscoverable(
                        eq(null), eq(false), anyList(), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));
        stubGuideUserUniversity(gid, uid, univId, false);

        Page<TourSummaryResponse> res =
                service().list(null, null, "", TourDiscoverySort.RECOMMENDED, 0, 20);

        assertEquals(1, res.getTotalElements());
        assertNull(res.getContent().get(0).topic());
        assertEquals("Guide", res.getContent().get(0).guideDisplayName());
    }

    @Test
    void getById_nullTopic_missingUser_nullLanguages_useFallbacks() {
        UUID oid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UUID univId = UUID.randomUUID();
        TourOfferingEntity row = offering(oid, gid, univId);
        row.setTopic(null);
        row.setLanguages(null);
        when(offerings.findDiscoverableById(oid)).thenReturn(Optional.of(row));
        stubGuideUserUniversity(gid, uid, univId, false);

        TourDetailResponse res = service().getById(oid);

        assertNull(res.topic());
        assertEquals("Guide", res.guideDisplayName());
        assertEquals(List.of(), res.languages());
    }

    @Test
    void getById_throws404_whenUniversityMissingFromLookup() {
        UUID oid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UUID univId = UUID.randomUUID();
        when(offerings.findDiscoverableById(oid))
                .thenReturn(Optional.of(offering(oid, gid, univId)));
        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(gid);
        guide.setUserId(uid);
        when(guides.findAllById(List.of(gid))).thenReturn(List.of(guide));
        when(universities.findAllById(List.of(univId))).thenReturn(List.of());

        assertThrows(NotFoundException.class, () -> service().getById(oid));
    }

    @Test
    void getById_nullAvgRating_yieldsZero() {
        UUID oid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UUID univId = UUID.randomUUID();
        TourOfferingEntity row = offering(oid, gid, univId);
        row.setAvgRating(null);
        when(offerings.findDiscoverableById(oid)).thenReturn(Optional.of(row));
        stubGuideUserUniversity(gid, uid, univId, true);

        assertEquals(0.0, service().getById(oid).avgRating());
    }

    // ---- readLanguages branches ----

    @Test
    void getById_blankLanguages_yieldEmptyList() {
        assertEquals(List.of(), detailWithLanguages("   ").languages());
    }

    @Test
    void getById_malformedLanguages_yieldEmptyList() {
        assertEquals(List.of(), detailWithLanguages("not-json").languages());
    }

    @Test
    void getById_jsonNullLanguages_yieldEmptyList() {
        assertEquals(List.of(), detailWithLanguages("null").languages());
    }

    @Test
    void getById_filtersNullAndBlankLanguageEntries() {
        TourDetailResponse res = detailWithLanguages("[\"en-US\", null, \"  \", \"fr-FR\"]");
        assertEquals(List.of("en-US", "fr-FR"), res.languages());
    }

    // ---- helpers ----

    private void stubGuideUserUniversity(UUID gid, UUID uid, UUID univId, boolean userPresent) {
        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(gid);
        guide.setUserId(uid);
        guide.setBio("Bio");
        when(guides.findAllById(List.of(gid))).thenReturn(List.of(guide));
        if (userPresent) {
            UserEntity user = new UserEntity();
            user.setId(uid);
            user.setDisplayName("Maya Chen");
            when(users.findAllById(List.of(uid))).thenReturn(List.of(user));
        } else {
            when(users.findAllById(List.of(uid))).thenReturn(List.of());
        }
        UniversityEntity u = new UniversityEntity();
        u.setId(univId);
        u.setName("North Coast University");
        u.setSlug("north-coast");
        u.setCity("Arcata");
        u.setRegion("CA");
        when(universities.findAllById(List.of(univId))).thenReturn(List.of(u));
    }

    private TourDetailResponse detailWithLanguages(String languagesJson) {
        UUID oid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UUID univId = UUID.randomUUID();
        TourOfferingEntity row = offering(oid, gid, univId);
        row.setLanguages(languagesJson);
        when(offerings.findDiscoverableById(oid)).thenReturn(Optional.of(row));
        stubGuideUserUniversity(gid, uid, univId, true);
        return service().getById(oid);
    }
}
