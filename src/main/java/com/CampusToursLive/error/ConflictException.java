package com.CampusToursLive.error;

/**
 * The request is well-formed but conflicts with the CURRENT state of the resource — retrying the
 * same request later (or after changing the conflicting state) may succeed. Maps to HTTP 409 in the
 * web layer; distinct from {@link ValidationException} (422), whose requests are wrong in
 * themselves. Introduced for the reschedule flow (CTL-50): booking not in a reschedulable state,
 * proposed slot taken, or a proposal already pending.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
