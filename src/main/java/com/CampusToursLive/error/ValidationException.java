package com.CampusToursLive.error;

/**
 * The request is well-formed but violates a business rule (bad/missing field, illegal value). The
 * web layer maps it to HTTP 422 (see {@code GlobalExceptionHandler}).
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
