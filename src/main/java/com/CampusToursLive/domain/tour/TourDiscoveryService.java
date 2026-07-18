package com.CampusToursLive.domain.tour;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.TourDetailResponse;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public marketplace catalog — read-only discovery of ACTIVE offerings from APPROVED guides. Draft,
 * paused, archived, and unapproved-guide offerings are never returned.
 */
@Service
public class TourDiscoveryService {

    private static final int MAX_LIMIT = 50;

    /**
     * Offerings created within this many days are flagged {@code isNew} on the marketplace card.
     */
    private static final int NEW_WINDOW_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(TourDiscoveryService.class);

    private final TourOfferingRepository offerings;
    private final GuideProfileRepository guides;
    private final UniversityRepository universities;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public TourDiscoveryService(
            TourOfferingRepository offerings,
            GuideProfileRepository guides,
            UniversityRepository universities,
            UserRepository users,
            ObjectMapper mapper) {
        this.offerings = offerings;
        this.guides = guides;
        this.universities = universities;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<TourSummaryResponse> list(
            String universityIdRaw,
            String topicRaw,
            String q,
            TourDiscoverySort sort,
            int page,
            int limit) {
        UUID universityId = parseOptionalUniversityId(universityIdRaw);
        TourTopic topic = parseOptionalTopic(topicRaw);
        String query = escapeLike(q == null ? "" : q.trim());
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        int safePage = Math.max(page, 0);

        Page<TourOfferingEntity> rows =
                offerings.findDiscoverable(
                        universityId, topic, query, PageRequest.of(safePage, capped, toSort(sort)));

        Lookup lookup = loadLookup(rows.getContent());
        return rows.map(o -> toSummary(o, lookup));
    }

    @Transactional(readOnly = true)
    public TourDetailResponse getById(UUID tourId) {
        TourOfferingEntity offering =
                offerings
                        .findDiscoverableById(tourId)
                        .orElseThrow(() -> new NotFoundException("Tour not found"));
        Lookup lookup = loadLookup(List.of(offering));
        return toDetail(offering, lookup);
    }

    public static TourDiscoverySort parseSort(String raw) {
        if (raw == null || raw.isBlank()) return TourDiscoverySort.RECOMMENDED;
        try {
            return TourDiscoverySort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid sort: " + raw);
        }
    }

    private UUID parseOptionalUniversityId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid universityId: " + raw);
        }
    }

    private TourTopic parseOptionalTopic(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return TourTopic.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid topic: " + raw);
        }
    }

    /**
     * Escape LIKE wildcards so a client-supplied {@code q} is matched literally (e.g. {@code %} and
     * {@code _} are not treated as wildcards). Pairs with {@code escape '!'} in the JPQL.
     */
    static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static Sort toSort(TourDiscoverySort sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc("priceCents"), Sort.Order.asc("title"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("priceCents"), Sort.Order.asc("title"));
            case RATING ->
                    Sort.by(
                            Sort.Order.desc("avgRating"),
                            Sort.Order.desc("reviewCount"),
                            Sort.Order.asc("title"));
            case RECOMMENDED ->
                    Sort.by(
                            Sort.Order.desc("avgRating"),
                            Sort.Order.desc("reviewCount"),
                            Sort.Order.desc("createdAt"));
        };
    }

    private Lookup loadLookup(List<TourOfferingEntity> rows) {
        if (rows.isEmpty()) return Lookup.empty();

        List<UUID> guideIds = rows.stream().map(TourOfferingEntity::getGuideId).distinct().toList();
        List<UUID> universityIds =
                rows.stream().map(TourOfferingEntity::getUniversityId).distinct().toList();

        Map<UUID, GuideProfileEntity> guideById = new HashMap<>();
        for (GuideProfileEntity g : guides.findAllById(guideIds)) {
            guideById.put(g.getId(), g);
        }

        Map<UUID, UserEntity> userById = new HashMap<>();
        List<UUID> userIds =
                guideById.values().stream().map(GuideProfileEntity::getUserId).distinct().toList();
        for (UserEntity u : users.findAllById(userIds)) {
            userById.put(u.getId(), u);
        }

        Map<UUID, UniversityEntity> universityById = new HashMap<>();
        for (UniversityEntity u : universities.findAllById(universityIds)) {
            universityById.put(u.getId(), u);
        }

        return new Lookup(guideById, userById, universityById);
    }

    private TourSummaryResponse toSummary(TourOfferingEntity o, Lookup lookup) {
        GuideProfileEntity guide = requireGuide(o, lookup);
        UniversityEntity university = requireUniversity(o, lookup);
        UserEntity user = lookup.userById().get(guide.getUserId());
        String guideName = user != null ? user.getDisplayName() : "Guide";

        return new TourSummaryResponse(
                o.getId().toString(),
                o.getTitle(),
                o.getSlug(),
                o.getTopic() != null ? o.getTopic().name() : null,
                university.getId().toString(),
                university.getName(),
                guide.getId().toString(),
                guideName,
                guide.getMajor(),
                guide.getDegree(),
                guide.getEntryYear(),
                o.getDurationMin(),
                o.getPriceCents(),
                o.getCurrency(),
                toRating(o.getAvgRating()),
                o.getReviewCount(),
                readStringArray(o.getLanguages()),
                readStringArray(o.getFeatures()),
                isNew(o));
    }

    private static boolean isNew(TourOfferingEntity o) {
        Instant created = o.getCreatedAt();
        return created != null
                && created.isAfter(Instant.now().minus(NEW_WINDOW_DAYS, ChronoUnit.DAYS));
    }

    private TourDetailResponse toDetail(TourOfferingEntity o, Lookup lookup) {
        GuideProfileEntity guide = requireGuide(o, lookup);
        UniversityEntity university = requireUniversity(o, lookup);
        UserEntity user = lookup.userById().get(guide.getUserId());
        String guideName = user != null ? user.getDisplayName() : "Guide";

        return new TourDetailResponse(
                o.getId().toString(),
                o.getTitle(),
                o.getSlug(),
                o.getTopic() != null ? o.getTopic().name() : null,
                o.getDescription(),
                readStringArray(o.getLanguages()),
                university.getId().toString(),
                university.getName(),
                university.getSlug(),
                university.getCity(),
                university.getRegion(),
                guide.getId().toString(),
                guideName,
                guide.getBio(),
                o.getDurationMin(),
                o.getPriceCents(),
                o.getCurrency(),
                toRating(o.getAvgRating()),
                o.getReviewCount());
    }

    private GuideProfileEntity requireGuide(TourOfferingEntity o, Lookup lookup) {
        GuideProfileEntity guide = lookup.guideById().get(o.getGuideId());
        if (guide == null) {
            throw new NotFoundException("Tour not found");
        }
        return guide;
    }

    private UniversityEntity requireUniversity(TourOfferingEntity o, Lookup lookup) {
        UniversityEntity university = lookup.universityById().get(o.getUniversityId());
        if (university == null) {
            throw new NotFoundException("Tour not found");
        }
        return university;
    }

    private static double toRating(BigDecimal avgRating) {
        return avgRating == null ? 0.0 : avgRating.doubleValue();
    }

    /** Parse a JSONB string array (languages / features), dropping null/blank entries. */
    private List<String> readStringArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> values = mapper.readValue(json, new TypeReference<>() {});
            return values == null
                    ? List.of()
                    : values.stream().filter(s -> s != null && !s.isBlank()).toList();
        } catch (Exception ex) {
            log.debug("Ignoring malformed JSON string array: {}", json, ex);
            return List.of();
        }
    }

    private record Lookup(
            Map<UUID, GuideProfileEntity> guideById,
            Map<UUID, UserEntity> userById,
            Map<UUID, UniversityEntity> universityById) {

        static Lookup empty() {
            return new Lookup(Map.of(), Map.of(), Map.of());
        }
    }
}
