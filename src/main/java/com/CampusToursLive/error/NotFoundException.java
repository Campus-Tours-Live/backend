package com.CampusToursLive.error;

import java.util.Map;

/**
 * A requested resource does not exist. Framework-agnostic — the web layer maps it to HTTP 404 (see
 * {@code GlobalExceptionHandler}); the domain stays free of Spring Web types.
 *
 * <p>Optionally carries a machine-readable {@link CodedProblem#code()} (e.g. {@code
 * "ACCOUNT_NOT_PROVISIONED"}) for callers that need one. Existing call sites using the message-only
 * constructor are unaffected — {@link #code()} defaults to {@code null} and {@link #properties()}
 * to an empty map.
 */
public class NotFoundException extends RuntimeException implements CodedProblem {

    private final String code;
    private final transient Map<String, Object> properties;

    public NotFoundException(String message) {
        this(message, null, Map.of());
    }

    public NotFoundException(String message, String code) {
        this(message, code, Map.of());
    }

    public NotFoundException(String message, String code, Map<String, Object> properties) {
        super(message);
        this.code = code;
        this.properties = properties;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public Map<String, Object> properties() {
        return properties;
    }
}
