package com.CampusToursLive.domain.booking;

/**
 * Display-only status of a DRAFT cart item, computed fresh each time the cart is read (CTL-83). It
 * is a hint for the UI — NOT an atomic reservation: {@code AVAILABLE} here never guarantees the
 * slot is held, and checkout (when it ships) must still re-validate. Ordered most- to least-
 * blocking; the first condition that matches wins.
 */
public enum CartItemStatus {
    /** The scheduled start is already in the past. */
    EXPIRED,
    /** The offering no longer exists or is not ACTIVE (also covers an inactive university). */
    TOUR_UNAVAILABLE,
    /** The offering's guide is no longer APPROVED / bookable. */
    GUIDE_UNAVAILABLE,
    /** The time is no longer within the guide's published availability. */
    TIME_UNAVAILABLE,
    /** Still bookable, but the offering's price changed since it was added to the cart. */
    PRICE_CHANGED,
    /** Bookable and unchanged as far as this display check can tell. */
    AVAILABLE
}
