package com.CampusToursLive.domain.saved;

import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.error.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A participant's saved tours (wishlist). Only marketplace-visible offerings can be saved; save and
 * unsave are both idempotent. The caller's role is enforced by the web layer — this service only
 * ever acts on the {@code userId} it is handed.
 */
@Service
public class SavedTourService {

    private final SavedTourRepository savedTours;
    private final TourOfferingRepository offerings;

    public SavedTourService(SavedTourRepository savedTours, TourOfferingRepository offerings) {
        this.savedTours = savedTours;
        this.offerings = offerings;
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
}
