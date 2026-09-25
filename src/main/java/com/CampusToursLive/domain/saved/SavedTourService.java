package com.CampusToursLive.domain.saved;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A participant's saved tours (wishlist). Only marketplace-visible offerings can be saved; save and
 * unsave are both idempotent. The caller's role is enforced by the web layer — this service only
 * ever acts on the {@code userId} it is handed.
 */
@Service
public class SavedTourService {

    /** Same page-size cap as the public catalog. */
    private static final int MAX_LIMIT = 50;

    private final SavedTourRepository savedTours;
    private final TourOfferingRepository offerings;
    private final TourDiscoveryService discovery;

    public SavedTourService(
            SavedTourRepository savedTours,
            TourOfferingRepository offerings,
            TourDiscoveryService discovery) {
        this.savedTours = savedTours;
        this.offerings = offerings;
        this.discovery = discovery;
    }

    /**
     * Saves the offering for the user. Returns {@code true} when newly saved, {@code false} when it
     * was already saved.
     *
     * @throws NotFoundException when the offering does not exist or is not marketplace-visible.
     */
    @Transactional
    public boolean save(UUID userId, UUID tourOfferingId) {
        if (offerings.findDiscoverableById(tourOfferingId).isEmpty()) {
            throw new NotFoundException("Tour not found");
        }
        return savedTours.insertIfAbsent(UUID.randomUUID(), userId, tourOfferingId) > 0;
    }

    /** Removes the save if present; a missing save is not an error. */
    @Transactional
    public void unsave(UUID userId, UUID tourOfferingId) {
        savedTours.deleteByUserIdAndTourOfferingId(userId, tourOfferingId);
    }

    /**
     * The user's saved tours as marketplace cards, most recently saved first. Saves whose offering
     * is no longer marketplace-visible are left out of both the page and its total.
     */
    @Transactional(readOnly = true)
    public Page<TourSummaryResponse> list(UUID userId, int page, int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), capped);
        Page<TourOfferingEntity> rows = savedTours.findVisibleSavedOfferings(userId, pageable);
        List<TourSummaryResponse> cards = discovery.toSummaries(rows.getContent());
        return new PageImpl<>(cards, pageable, rows.getTotalElements());
    }

    /**
     * Every offering id the user has saved, most recent first — lets the catalog light its hearts
     * without fetching full cards. Not filtered by visibility: an id that is no longer listed
     * simply never matches a catalog card.
     */
    @Transactional(readOnly = true)
    public List<UUID> savedTourIds(UUID userId) {
        return savedTours.findTourOfferingIdsByUserId(userId);
    }
}
