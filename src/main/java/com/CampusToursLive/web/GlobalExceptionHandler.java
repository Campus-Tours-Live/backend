package com.CampusToursLive.web;

import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.UnauthorizedException;
import com.CampusToursLive.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Normalises every error to RFC 7807 problem+json (Spring's ProblemDetail) — the single error
 * format the BFF and Core share. This advice is the ONLY place that knows the HTTP status for a
 * domain error: the domain/service layer throws framework-agnostic exceptions ({@link
 * ValidationException}, {@link NotFoundException}, {@link ForbiddenException}, {@link
 * UnauthorizedException}) and stays free of Spring Web.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Business rule violated (bad/missing field, illegal value) → 422. */
    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /** Domain resource missing → 404. */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleDomainNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Authenticated but not allowed (missing role, gate not met) → 403. */
    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadArg(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("Validation failed");
        pd.setDetail(ex.getMessage());
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
