package com.CampusToursLive.web;

import com.CampusToursLive.error.CodedProblem;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.UnauthorizedException;
import com.CampusToursLive.error.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Normalises every error to RFC 7807 problem+json (Spring's ProblemDetail) — the single error
 * format the BFF and Core share. This advice is the ONLY place that knows the HTTP status for a
 * domain error: the domain/service layer throws framework-agnostic exceptions ({@link
 * ValidationException}, {@link NotFoundException}, {@link ForbiddenException}, {@link
 * UnauthorizedException}, {@link ConflictException}) and stays free of Spring Web. {@link
 * NotFoundException}, {@link ForbiddenException}, and {@link ConflictException} may additionally
 * implement {@link CodedProblem} to carry a machine-readable {@code code} shared with bff/OpenAPI.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Business rule violated (bad/missing field, illegal value) → 422. */
    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /**
     * Domain resource missing → 404, plus a machine {@code code} when the exception carries one.
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleDomainNotFound(NotFoundException ex) {
        ProblemDetail pd = problem(HttpStatus.NOT_FOUND, ex.getMessage());
        applyCode(pd, ex);
        return pd;
    }

    /**
     * Authenticated but not allowed (missing role, gate not met) → 403, plus a machine {@code code}
     * when the exception carries one.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN, ex.getMessage());
        applyCode(pd, ex);
        return pd;
    }

    /**
     * Request conflicts with the current state of a resource (role already granted, ineligible
     * role, or a data-integrity invariant broken) → 409, with a machine {@code code} and structured
     * properties (e.g. {@code reconciliationRequired}) for the client.
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, ex.getMessage());
        applyCode(pd, ex);
        return pd;
    }

    /** No valid principal / no provisioned account → 401. */
    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * Fallback for any Spring {@link ResponseStatusException} a controller may raise: honor its
     * status.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(ex.getStatusCode());
        pd.setTitle(ex.getReason());
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String title) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        return pd;
    }

    /**
     * Copies a {@link CodedProblem}'s machine {@code code} and structured {@code properties} onto
     * the problem — shared by every handler whose exception MAY carry one (404/403/409). This is
     * the ONLY place that reads a coded exception's fields; no handler branches on the exception's
     * message string. A {@code null} code (legacy/uncoded exceptions) leaves the problem unchanged,
     * so existing behavior is preserved.
     */
    private static void applyCode(ProblemDetail pd, CodedProblem ex) {
        if (ex.code() != null) {
            pd.setProperty("code", ex.code());
        }
        ex.properties().forEach(pd::setProperty);
    }

    /**
     * Optimistic-lock conflict — two requests updated the same row (@Version) concurrently and this
     * one lost → 409, so clients retry instead of treating it as a server fault.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "This resource was modified by another request — please retry");
    }

    /**
     * An unmapped route must be a 404 — not a 500 via the catch-all below. (Common symptom: calling
     * a new endpoint before the server was rebuilt.)
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ProblemDetail handleNotFound(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Not found");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    /** Malformed typed path/query param (e.g. a non-UUID {@code {tourId}}) → 422, not a 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("Validation failed");
        pd.setDetail("Invalid value for '" + ex.getName() + "'");
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadArg(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("Validation failed");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    /**
     * Bean-validation failure on a {@code @Valid @RequestBody} (e.g. an onboarding command missing
     * a first-time-required field — see {@code GuideOnboardingRequest} / {@code
     * ParticipantOnboardingRequest}) → 422 {@code VALIDATION_FAILED}, overriding Spring's default
     * of 400 for this exception. Core's contract reserves 400 for a structurally malformed body
     * (see {@link #handleMalformedBody}) and 422 for a well-formed-but-invalid one, matching how
     * {@link ValidationException} is already mapped. Per-field messages are surfaced under {@code
     * errors} for client-side form binding when present.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed");
        pd.setProperty("code", "VALIDATION_FAILED");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        if (!errors.isEmpty()) {
            pd.setProperty("errors", errors);
        }
        return pd;
    }

    /**
     * A structurally malformed request body (unparsable JSON) stays 400 — distinct from the 422
     * bean-validation failures above, which require a well-formed body. Spring's own default for
     * this exception is already 400; this handler exists only so the broad {@link
     * #handleGeneric(Exception)} catch-all below (which would otherwise also match, at 500) never
     * takes priority.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedBody(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Malformed request body");
        return pd;
    }

    /**
     * Catch-all. The exception message can carry internals (SQL, class names, stack hints), so it
     * is logged server-side but NOT returned to the client — the response stays a generic
     * problem+json.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal server error");
        pd.setDetail("An unexpected error occurred.");
        return pd;
    }
}
