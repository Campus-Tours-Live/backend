package com.CampusToursLive.error;

import java.util.Map;

/**
 * The caller is authenticated but not allowed to perform this action (missing role, gate not met).
 * The web layer maps it to HTTP 403 (see {@code GlobalExceptionHandler}).
 *
 * <p>Optionally carries a machine-readable {@link CodedProblem#code()} (e.g. {@code
 * "ROLE_REQUIRED"}) for callers that need one. Existing call sites using the message-only
 * constructor are unaffected — {@link #code()} defaults to {@code null} and {@link #properties()}
 * to an empty map.
 */
public class ForbiddenException extends RuntimeException implements CodedProblem {

    private final String code;
    private final transient Map<String, Object> properties;

    public ForbiddenException(String message) {
        this(message, null, Map.of());
    }

    public ForbiddenException(String message, String code) {
        this(message, code, Map.of());
    }

    public ForbiddenException(String message, String code, Map<String, Object> properties) {
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
