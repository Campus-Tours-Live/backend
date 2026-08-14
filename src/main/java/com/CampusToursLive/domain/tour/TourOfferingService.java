package com.CampusToursLive.domain.tour;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.guide.GuideUniversityRepository;
import com.CampusToursLive.domain.guide.GuideVerificationStatus;
import com.CampusToursLive.domain.university.CampusImageUrls;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.web.dto.CreateOfferingRequest;
import com.CampusToursLive.web.dto.TourOfferingResponse;
import com.CampusToursLive.web.dto.UpdateOfferingRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tour offerings — a guide's supply-side products. This is the first endpoint set that actually
 * exercises the role/approval gates: - creating a DRAFT is allowed while the application is still
 * pending (preparing unpublished content is not a "live" action); - activating (going live,
 * DRAFT→ACTIVE) requires guide_status == VERIFIED. The caller already enforced the GUIDE role
 * (controller: requireRole(GUIDE)).
 */
@Service
public class TourOfferingService {

    private static final Set<Integer> DURATIONS = Set.of(30, 45, 60, 90);
    private static final long MIN_PRICE_CENTS = 2000L;
    private static final long MAX_PRICE_CENTS = 20000L;

    private final TourOfferingRepository offerings;
    private final GuideProfileRepository guides;
    private final GuideUniversityRepository guideUniversities;
    private final UniversityRepository universities;
    private final CampusImageUrls campusImages;
    private final ObjectMapper mapper;

    public TourOfferingService(
            TourOfferingRepository offerings,
            GuideProfileRepository guides,
            GuideUniversityRepository guideUniversities,
            UniversityRepository universities,
            CampusImageUrls campusImages,
            ObjectMapper mapper) {
        this.offerings = offerings;
        this.guides = guides;
        this.guideUniversities = guideUniversities;
        this.universities = universities;
        this.campusImages = campusImages;
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

        UUID universityId = parseUuid(req.universityId());
        if (!isGuidesVerifiedUniversity(guide, universityId)) {
            throw new ValidationException(
                    "universityId must be a university you are verified for (verification_status"
                            + " must be VERIFIED)");
        }
        // Backfill the campus image on first use if the university has none yet (idempotent).
        universities
                .findById(universityId)
                .ifPresent(
                        u -> {
                            if (u.getImageUrl() == null || u.getImageUrl().isBlank()) {
                                u.setImageUrl(campusImages.forName(u.getName()));
                                universities.save(u);
                            }
                        });
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
            List<String> langs = SupportedLanguages.requireSupported(req.languages());
            if (!langs.isEmpty()) o.setLanguages(writeJson(langs));
        }
        if (req.features() != null) {
            o.setFeatures(writeJson(validateFeatures(req.features(), topic)));
        }
        // status defaults to DRAFT — creating unpublished content is allowed while pending.
        offerings.save(o);
        return toResponse(o);
    }

    @Transactional
    public TourOfferingResponse activate(UserEntity user, UUID offeringId) {
        GuideProfileEntity guide = requireGuideProfile(user);

        // Live-action gate: publishing a draft (DRAFT -> ACTIVE) requires a VERIFIED guide
        // application.
        if (guide.getStatus() != GuideStatus.VERIFIED) {
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

    /** Updates a non-public offering. Guides pause an active offering before changing it. */
    @Transactional
    public TourOfferingResponse update(
            UserEntity user, UUID offeringId, UpdateOfferingRequest req) {
        GuideProfileEntity guide = requireGuideProfile(user);
        TourOfferingEntity offering = findOwned(offeringId, guide);
        if (offering.getStatus() != TourStatus.DRAFT && offering.getStatus() != TourStatus.PAUSED) {
            throw new ValidationException("Only a draft or paused offering can be edited");
        }
        if (req == null || req.isEmpty()) {
            throw new ValidationException("Provide at least one field to update");
        }

        TourTopic nextTopic = offering.getTopic();
        if (req.topic() != null) nextTopic = parseTopic(req.topic());

        if (req.universityId() != null) {
            UUID universityId = parseUuid(req.universityId());
            if (!isGuidesVerifiedUniversity(guide, universityId)) {
                throw new ValidationException(
                        "universityId must be a university you are verified for");
            }
            offering.setUniversityId(universityId);
        }
        if (req.title() != null) {
            String title = requireTitle(req.title());
            if (!title.equals(offering.getTitle())) {
                offering.setSlug(uniqueSlug(guide.getId(), slugify(title)));
            }
            offering.setTitle(title);
        }
        if (req.durationMin() != null) offering.setDurationMin(requireDuration(req.durationMin()));
        if (req.priceCents() != null) offering.setPriceCents(requirePrice(req.priceCents()));
        if (req.description() != null) offering.setDescription(req.description().trim());
        offering.setTopic(nextTopic);
        if (req.languages() != null)
            offering.setLanguages(writeJson(SupportedLanguages.requireSupported(req.languages())));
        if (req.features() != null) {
            offering.setFeatures(writeJson(validateFeatures(req.features(), nextTopic)));
        } else if (req.topic() != null) {
            // A topic change cannot leave feature codes that the new topic does not permit.
            validateFeatures(readStringArray(offering.getFeatures()), nextTopic);
        }
        offerings.save(offering);
        return toResponse(offering);
    }

    /** Stops new marketplace bookings while preserving the offering for a later re-publish. */
    @Transactional
    public TourOfferingResponse pause(UserEntity user, UUID offeringId) {
        TourOfferingEntity offering = findOwned(offeringId, requireGuideProfile(user));
        if (offering.getStatus() == TourStatus.PAUSED) return toResponse(offering);
        if (offering.getStatus() != TourStatus.ACTIVE) {
            throw new ValidationException("Only an active offering can be paused");
        }
        offering.setStatus(TourStatus.PAUSED);
        offerings.save(offering);
        return toResponse(offering);
    }

    /**
     * Permanently removes an offering from discovery. Existing bookings are intentionally
     * unchanged.
     */
    @Transactional
    public TourOfferingResponse retire(UserEntity user, UUID offeringId) {
        TourOfferingEntity offering = findOwned(offeringId, requireGuideProfile(user));
        if (offering.getStatus() == TourStatus.ARCHIVED) return toResponse(offering);
        offering.setStatus(TourStatus.ARCHIVED);
        offerings.save(offering);
        return toResponse(offering);
    }

    /** Clones an owned offering into an unpublished draft with a unique copy title and slug. */
    @Transactional
    public TourOfferingResponse duplicate(UserEntity user, UUID offeringId) {
        GuideProfileEntity guide = requireGuideProfile(user);
        TourOfferingEntity source = findOwned(offeringId, guide);
        TourOfferingEntity copy = new TourOfferingEntity();
        copy.setId(UUID.randomUUID());
        copy.setGuideId(guide.getId());
        copy.setUniversityId(source.getUniversityId());
        copy.setTitle(uniqueCopyTitle(guide.getId(), source.getTitle()));
        copy.setSlug(uniqueSlug(guide.getId(), slugify(copy.getTitle())));
        copy.setDescription(source.getDescription());
        copy.setTopic(source.getTopic());
        copy.setDurationMin(source.getDurationMin());
        copy.setPriceCents(source.getPriceCents());
        copy.setCurrency(source.getCurrency());
        copy.setLanguages(source.getLanguages());
        copy.setFeatures(source.getFeatures());
        copy.setStatus(TourStatus.DRAFT);
        offerings.save(copy);
        return toResponse(copy);
    }

    private TourOfferingEntity findOwned(UUID offeringId, GuideProfileEntity guide) {
        return offerings
                .findByIdAndGuideId(offeringId, guide.getId())
                .orElseThrow(() -> new NotFoundException("Offering not found"));
    }

    private GuideProfileEntity requireGuideProfile(UserEntity user) {
        return guides.findByUserId(user.getId())
                .orElseThrow(
                        () ->
                                new ValidationException(
                                        "No guide profile — complete guide onboarding first"));
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("universityId is required");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid universityId: " + raw);
        }
    }

    /**
     * Whether {@code id} is a university the guide is verified for: a {@code guide_universities}
     * row exists for {@code (guide.id, id)} with {@code verification_status == VERIFIED}. A guide
     * may hold several schools; only a VERIFIED membership counts.
     */
    private boolean isGuidesVerifiedUniversity(GuideProfileEntity guide, UUID id) {
        return guideUniversities.findByGuideProfileId(guide.getId()).stream()
                .anyMatch(
                        g ->
                                id.equals(g.getUniversityId())
                                        && g.getVerificationStatus()
                                                == GuideVerificationStatus.VERIFIED);
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

    private static String requireTitle(String raw) {
        String title = raw == null ? null : raw.trim();
        if (title == null || title.isEmpty()) throw new ValidationException("title is required");
        return title;
    }

    private static int requireDuration(Integer duration) {
        if (duration == null || !DURATIONS.contains(duration)) {
            throw new ValidationException("durationMin must be one of 30, 45, 60, 90");
        }
        return duration;
    }

    private static long requirePrice(Long price) {
        if (price == null || price < MIN_PRICE_CENTS || price > MAX_PRICE_CENTS) {
            throw new ValidationException(
                    "priceCents must be between " + MIN_PRICE_CENTS + " and " + MAX_PRICE_CENTS);
        }
        return price;
    }

    private String uniqueSlug(UUID guideId, String candidate) {
        String base = candidate.isBlank() ? "tour" : candidate;
        String slug = base;
        int suffix = 2;
        while (offerings.existsByGuideIdAndSlug(guideId, slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private String uniqueCopyTitle(UUID guideId, String title) {
        String base = "Copy of " + (title == null || title.isBlank() ? "tour" : title.trim());
        String candidate = base;
        int suffix = 2;
        while (offerings.existsByGuideIdAndSlug(guideId, slugify(candidate))) {
            candidate = base + " " + suffix++;
        }
        return candidate;
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

    /**
     * Validate a client-supplied feature selection: each must be a known {@link TourFeature} that
     * is allowed for the offering's topic; duplicates are dropped; at most {@link
     * TourFeature#MAX_PER_OFFERING} may be kept. Returns the enum names to persist.
     */
    private List<String> validateFeatures(List<String> raw, TourTopic topic) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            TourFeature feature;
            try {
                feature = TourFeature.valueOf(s.trim());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Unknown feature: " + s);
            }
            if (!TourFeatureCatalog.isAllowed(topic, feature)) {
                throw new ValidationException(
                        "Feature " + feature.name() + " is not available for topic " + topic);
            }
            out.add(feature.name());
        }
        if (out.size() > TourFeature.MAX_PER_OFFERING) {
            throw new ValidationException(
                    "At most " + TourFeature.MAX_PER_OFFERING + " features may be selected");
        }
        return List.copyOf(out);
    }

    private List<String> readStringArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> values = mapper.readValue(json, new TypeReference<>() {});
            return values == null
                    ? List.of()
                    : values.stream().filter(s -> s != null && !s.isBlank()).toList();
        } catch (Exception ex) {
            return List.of();
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
                o.getDescription(),
                readStringArray(o.getLanguages()),
                readStringArray(o.getFeatures()));
    }
}
