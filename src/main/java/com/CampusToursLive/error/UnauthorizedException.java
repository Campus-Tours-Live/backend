package com.CampusToursLive.error;

/**
 * No (valid) authenticated principal, or the principal has no provisioned account. The web layer
 * maps it to HTTP 401 (see {@code GlobalExceptionHandler}).
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
