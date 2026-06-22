package com.CampusToursLive.domain.tour;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.CreateOfferingRequest;
import com.CampusToursLive.web.dto.TourOfferingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tour offerings — a guide's supply-side products. This is the first endpoint set that actually
 * exercises the role/approval gates: - creating a DRAFT is allowed while the application is still
 * pending (preparing unpublished content is not a "live" action); - activating (going live,
 * DRAFT→ACTIVE) requires application_status == APPROVED. The caller already enforced the GUIDE role
 * (controller: requireRole(GUIDE)).
 */
@Service
public class TourOfferingService {

    private static final Set<Integer> DURATIONS = Set.of(30, 45, 60, 90);
    private static final long MIN_PRICE_CENTS = 2000L;
    private static final long MAX_PRICE_CENTS = 20000L;

    private final TourOfferingRepository offerings;
    private final GuideProfileRepository guides;
    private final UniversityRepository universities;
    private final ObjectMapper mapper;

    public TourOfferingService(
            TourOfferingRepository offerings,
            GuideProfileRepository guides,
            UniversityRepository universities,
            ObjectMapper mapper) {
        this.offerings = offerings;
        this.guides = guides;
        this.universities = universities;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<TourOfferingResponse> listOwn(UserEntity user) {
        GuideProfileEntity guide = requireGuideProfile(user);
        return offerings.findByGuideId(guide.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TourOfferingResponse create(UserEntity user, CreateOfferingRequest req) {
        GuideProfileEntity guide = requireGuideProfile(user);

        UUID universityId = parseUniversity(req.universityId());
        String title = req.title() == null ? null : req.title().trim();
        if (title == null || title.isEmpty()) {
            throw new ValidationException("title is required");
        }
        TourTopic topic = parseTopic(req.topic());
        int duration = req.durationMin() == null ? 0 : req.durationMin();
        if (!DURATIONS.contains(duration)) {
            throw new ValidationException("durationMin must be one of 30, 45, 60, 90");
        }
        long price = req.priceCents() == null ? -1 : req.priceCents();
        if (price < MIN_PRICE_CENTS || price > MAX_PRICE_CENTS) {
            throw new ValidationException(
                    "priceCents must be between " + MIN_PRICE_CENTS + " and " + MAX_PRICE_CENTS);
        }
        String slug = slugify(title);
        if (slug.isEmpty()) slug = "tour";
        if (offerings.existsByGuideIdAndSlug(guide.getId(), slug)) {
            throw new ValidationException("You already have an offering with a similar title");
        }

        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guide.getId());
        o.setUniversityId(universityId);
        o.setTitle(title);
        o.setSlug(slug);
        if (req.description() != null) o.setDescription(req.description().trim());
        o.setTopic(topic);
        o.setDurationMin(duration);
        o.setPriceCents(price);
        if (req.languages() != null) {
            List<String> langs =
                    req.languages().stream().filter(s -> s != null && !s.isBlank()).toList();
            if (!langs.isEmpty()) o.setLanguages(writeJson(langs));
        }
        // status defaults to DRAFT — creating unpublished content is allowed while pending.
        offerings.save(o);
        return toResponse(o);
    }

    @Transactional
    public TourOfferingResponse activate(UserEntity user, UUID offeringId) {
        GuideProfileEntity guide = requireGuideProfile(user);

        // Live-action gate: publishing a draft (DRAFT -> ACTIVE) requires an APPROVED guide
        // application.
        if (guide.getApplicationStatus() != GuideApplicationStatus.APPROVED) {
            throw new ForbiddenException(
                    "Your guide application must be approved before you can publish offerings");
        }

        TourOfferingEntity o =
                offerings
                        .findByIdAndGuideId(offeringId, guide.getId())
                        .orElseThrow(() -> new NotFoundException("Offering not found"));

        if (o.getStatus() == TourStatus.ACTIVE) return toResponse(o); // idempotent
        if (o.getStatus() != TourStatus.DRAFT && o.getStatus() != TourStatus.PAUSED) {
            throw new ValidationException("Only a draft or paused offering can be activated");
        }
        o.setStatus(TourStatus.ACTIVE);
        offerings.save(o);
        return toResponse(o);
    }

    private GuideProfileEntity requireGuideProfile(UserEntity user) {
        return guides.findByUserId(user.getId())
                .orElseThrow(
                        () ->
                                new ValidationException(
                                        "No guide profile — complete guide onboarding first"));
    }

    private UUID parseUniversity(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("universityId is required");
        }
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

    private TourTopic parseTopic(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("topic is required");
        }
        try {
            return TourTopic.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid topic: " + raw);
        }
    }

    private static String slugify(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private TourOfferingResponse toResponse(TourOfferingEntity o) {
        return new TourOfferingResponse(
                o.getId().toString(),
                o.getTitle(),
                o.getSlug(),
                o.getStatus() != null ? o.getStatus().name() : null,
                o.getTopic() != null ? o.getTopic().name() : null,
                o.getUniversityId() != null ? o.getUniversityId().toString() : null,
                o.getDurationMin(),
                o.getPriceCents(),
                o.getCurrency(),
                o.getDescription());
    }
}
