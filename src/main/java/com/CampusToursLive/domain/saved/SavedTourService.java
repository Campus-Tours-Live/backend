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

/** A participant's saved tours. Role checks live in the web layer. */
@Service
public class SavedTourService {

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

    /** Returns true when newly saved, false when it was already saved. */
    @Transactional
    public boolean save(UUID userId, UUID tourOfferingId) {
        if (offerings.findDiscoverableById(tourOfferingId).isEmpty()) {
            throw new NotFoundException("Tour not found");
        }
        return savedTours.insertIfAbsent(UUID.randomUUID(), userId, tourOfferingId) > 0;
    }

    @Transactional
    public void unsave(UUID userId, UUID tourOfferingId) {
        savedTours.deleteByUserIdAndTourOfferingId(userId, tourOfferingId);
    }

    @Transactional(readOnly = true)
    public Page<TourSummaryResponse> list(UUID userId, int page, int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), capped);
        Page<TourOfferingEntity> rows = savedTours.findVisibleSavedOfferings(userId, pageable);
        List<TourSummaryResponse> cards = discovery.toSummaries(rows.getContent());
        return new PageImpl<>(cards, pageable, rows.getTotalElements());
    }

    /** Not filtered by visibility: a hidden tour's id just never matches a catalog card. */
    @Transactional(readOnly = true)
    public List<UUID> savedTourIds(UUID userId) {
        return savedTours.findTourOfferingIdsByUserId(userId);
    }
}
