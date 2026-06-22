package com.CampusToursLive.error;

/**
 * The caller is authenticated but not allowed to perform this action (missing role, gate not met).
 * The web layer maps it to HTTP 403 (see {@code GlobalExceptionHandler}).
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
