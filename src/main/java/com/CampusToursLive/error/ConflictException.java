package com.CampusToursLive.error;

/** Current-state conflict → HTTP 409 (distinct from {@link ValidationException} 422). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
