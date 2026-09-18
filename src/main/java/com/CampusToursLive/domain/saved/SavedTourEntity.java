package com.CampusToursLive.domain.saved;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps the {@code saved_tours} table (V3__saved_tours.sql) — a participant wishlist row. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "saved_tours")
public class SavedTourEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tour_offering_id", nullable = false)
    private UUID tourOfferingId;

    /** DB default now(); never written by the app. */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public SavedTourEntity(UUID id, UUID userId, UUID tourOfferingId) {
        this.id = id;
        this.userId = userId;
        this.tourOfferingId = tourOfferingId;
    }
}
