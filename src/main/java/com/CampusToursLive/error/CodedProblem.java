package com.CampusToursLive.error;

import java.util.Map;

/**
 * Machine-readable error contract shared across Core, bff, and the generated OpenAPI spec. Any
 * domain exception that needs a stable, parseable error code beyond its human-readable message
 * implements this so {@code GlobalExceptionHandler} can copy it onto the RFC 7807 {@code
 * ProblemDetail} regardless of the HTTP status it maps to. The handler is the ONLY place that reads
 * {@link #code()}/{@link #properties()} — it never branches on the exception's message string.
 */
public interface CodedProblem {

    /**
     * Machine-readable error code (e.g. {@code "ROLE_ALREADY_GRANTED"}), or {@code null} if this
     * exception instance carries none (legacy/uncoded usage).
     */
    String code();

    /**
     * Structured properties merged onto the {@code ProblemDetail} (e.g. {@code role}, {@code
     * reconciliationRequired}); empty if none.
     */
    Map<String, Object> properties();
}
