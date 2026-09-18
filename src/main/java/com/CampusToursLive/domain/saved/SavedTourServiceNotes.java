package com.CampusToursLive.domain.saved;

/**
 * Scratch notes for CTL-125 service shape (not wired yet).
 *
 * <ul>
 *   <li>save(userId, offeringId) — require discoverable; idempotent insert
 *   <li>unsave(userId, offeringId) — idempotent delete
 *   <li>list(userId, pageable) — join discoverable offerings, newest first
 *   <li>ids(userId) — light heart state for catalog cards
 * </ul>
 */
final class SavedTourServiceNotes {
    private SavedTourServiceNotes() {}
}
