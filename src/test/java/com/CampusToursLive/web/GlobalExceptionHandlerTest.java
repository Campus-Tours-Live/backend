package com.CampusToursLive.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.UnauthorizedException;
import com.CampusToursLive.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * GlobalExceptionHandler — error normalisation to RFC 7807 problem+json. The security-relevant case
 * is the catch-all: an arbitrary exception's message (which may carry SQL/internals) must NEVER
 * reach the client.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusException_keepsStatusAndReasonAsTitle() {
        ProblemDetail pd =
                handler.handleStatus(
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Missing required role: GUIDE"));
        assertEquals(403, pd.getStatus());
        assertEquals("Missing required role: GUIDE", pd.getTitle());
    }

    @Test
    void noHandler_mapsTo404NotFound() {
        ProblemDetail pd =
                handler.handleNotFound(new NoHandlerFoundException("GET", "/nope", null));
        assertEquals(404, pd.getStatus());
        assertEquals("Not found", pd.getTitle());
    }

    @Test
    void illegalArgument_mapsTo422WithMessageAsDetail() {
        ProblemDetail pd =
                handler.handleBadArg(new IllegalArgumentException("No enum constant BOGUS"));
        assertEquals(422, pd.getStatus());
        assertEquals("Validation failed", pd.getTitle());
        assertEquals("No enum constant BOGUS", pd.getDetail());
    }

    @Test
    void optimisticLockConflict_mapsTo409() {
        ProblemDetail pd =
                handler.handleOptimisticLock(
                        new org.springframework.dao.OptimisticLockingFailureException(
                                "Row was updated or deleted by another transaction"));
        assertEquals(409, pd.getStatus());
        assertEquals("This resource was modified by another request — please retry", pd.getTitle());
    }

    @Test
    void genericException_mapsTo500AndHidesInternals() {
        ProblemDetail pd =
                handler.handleGeneric(
                        new RuntimeException(
                                "ERROR: duplicate key value violates unique constraint \"users_email_key\""));
        assertEquals(500, pd.getStatus());
        assertEquals("Internal server error", pd.getTitle());
        // The raw message (SQL/constraint names) must not leak to the client.
        assertEquals("An unexpected error occurred.", pd.getDetail());
        assertFalse(String.valueOf(pd.getDetail()).contains("users_email_key"));
    }

    @Test
    void responseStatusException_withoutExplicitReason_stillCarriesStatus() {
        ProblemDetail pd = handler.handleStatus(new ResponseStatusException(HttpStatus.CONFLICT));
        assertEquals(409, pd.getStatus());
    }

    // ---- domain exceptions → status (message becomes the title) ----

    @Test
    void validationException_mapsTo422() {
        ProblemDetail pd = handler.handleValidation(new ValidationException("major is required"));
        assertEquals(422, pd.getStatus());
        assertEquals("major is required", pd.getTitle());
    }

    @Test
    void notFoundException_mapsTo404() {
        ProblemDetail pd =
                handler.handleDomainNotFound(new NotFoundException("Offering not found"));
        assertEquals(404, pd.getStatus());
        assertEquals("Offering not found", pd.getTitle());
    }

    @Test
    void forbiddenException_mapsTo403() {
        ProblemDetail pd =
                handler.handleForbidden(new ForbiddenException("Missing required role: ADMIN"));
        assertEquals(403, pd.getStatus());
        assertEquals("Missing required role: ADMIN", pd.getTitle());
    }

    @Test
    void unauthorizedException_mapsTo401() {
        ProblemDetail pd =
                handler.handleUnauthorized(new UnauthorizedException("Account not provisioned"));
        assertEquals(401, pd.getStatus());
        assertEquals("Account not provisioned", pd.getTitle());
    }

    // ---- CodedProblem contract: ConflictException (409) + coded 404/403 ----

    @Test
    void conflictException_roleAlreadyGranted_mapsTo409WithCodeAndReconciliationFlag() {
        ProblemDetail pd = handler.handleConflict(ConflictException.roleAlreadyGranted("GUIDE"));
        assertEquals(409, pd.getStatus());
        assertEquals("ROLE_ALREADY_GRANTED", pd.getProperties().get("code"));
        assertEquals("GUIDE", pd.getProperties().get("role"));
        assertEquals(true, pd.getProperties().get("reconciliationRequired"));
    }

    @Test
    void conflictException_roleNotEligible_mapsTo409WithCodeAndRole() {
        ProblemDetail pd = handler.handleConflict(ConflictException.roleNotEligible("GUIDE"));
        assertEquals(409, pd.getStatus());
        assertEquals("ROLE_NOT_ELIGIBLE", pd.getProperties().get("code"));
        assertEquals("GUIDE", pd.getProperties().get("role"));
    }

    @Test
    void conflictException_accountStateInvalid_mapsTo409WithCode() {
        ProblemDetail pd = handler.handleConflict(ConflictException.accountStateInvalid());
        assertEquals(409, pd.getStatus());
        assertEquals("ACCOUNT_STATE_INVALID", pd.getProperties().get("code"));
    }

    @Test
    void conflictException_roleProfileStateInvalid_mapsTo409WithCodeAndRole() {
        ProblemDetail pd =
                handler.handleConflict(ConflictException.roleProfileStateInvalid("GUIDE"));
        assertEquals(409, pd.getStatus());
        assertEquals("ROLE_PROFILE_STATE_INVALID", pd.getProperties().get("code"));
        assertEquals("GUIDE", pd.getProperties().get("role"));
    }

    @Test
    void notFoundException_withCode_mapsTo404WithCode() {
        ProblemDetail pd =
                handler.handleDomainNotFound(
                        new NotFoundException(
                                "Account not provisioned", "ACCOUNT_NOT_PROVISIONED"));
        assertEquals(404, pd.getStatus());
        assertEquals("Account not provisioned", pd.getTitle());
        assertEquals("ACCOUNT_NOT_PROVISIONED", pd.getProperties().get("code"));
    }

    @Test
    void notFoundException_withoutCode_hasNoCodeProperty() {
        ProblemDetail pd =
                handler.handleDomainNotFound(new NotFoundException("Offering not found"));
        assertEquals(404, pd.getStatus());
        assertNull(pd.getProperties());
    }

    @Test
    void forbiddenException_withCode_mapsTo403WithCode() {
        ProblemDetail pd =
                handler.handleForbidden(
                        new ForbiddenException("Missing required role: GUIDE", "ROLE_REQUIRED"));
        assertEquals(403, pd.getStatus());
        assertEquals("Missing required role: GUIDE", pd.getTitle());
        assertEquals("ROLE_REQUIRED", pd.getProperties().get("code"));
    }

    @Test
    void forbiddenException_withoutCode_hasNoCodeProperty() {
        ProblemDetail pd =
                handler.handleForbidden(new ForbiddenException("Missing required role: ADMIN"));
        assertEquals(403, pd.getStatus());
        assertNull(pd.getProperties());
    }
}
