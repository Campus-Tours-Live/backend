package com.CampusToursLive.error;

/**
 * A requested resource does not exist. Framework-agnostic — the web layer maps it to HTTP 404 (see
 * {@code GlobalExceptionHandler}); the domain stays free of Spring Web types.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
