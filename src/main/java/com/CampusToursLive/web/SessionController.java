package com.CampusToursLive.web;

import com.CampusToursLive.domain.user.RoleEligibilityService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleEntity;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.CurrentUserResponse;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.RoleEligibilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Session / identity endpoints. Paths are bare (no /v1) — the BFF strips the /v1 prefix before
 * calling Core, and calls /session directly at login time.
 *
 * <p>Core owns account facts only (identity, held roles). Current-role / session context — "which
 * role this browser session is currently using" — is owned entirely by the bff's server-side
 * session, never by Core: there is no current-role column, endpoint, or field here.
 */
@RestController
@Tag(
        name = "Session",
        description =
                "Session / identity. Resolves a login and returns the current principal (identity"
                        + " + held roles). Current-role/session context is bff-owned, not Core's.")
public class SessionController {

    private final CurrentUser currentUser;
    private final UserRoleRepository userRoles;
    private final RoleEligibilityService roleEligibility;

    public SessionController(
            CurrentUser currentUser,
            UserRoleRepository userRoles,
            RoleEligibilityService roleEligibility) {
        this.currentUser = currentUser;
        this.userRoles = userRoles;
        this.roleEligibility = roleEligibility;
    }

    /**
     * GET /users/me — read-only: the current authenticated principal (must already be provisioned).
     * Distinct from POST /session, which resolves/provisions.
     */
    @Operation(
            summary = "Current principal",
            description =
                    "Returns the current authenticated principal (identity and the authoritative"
                            + " role set). Read-only — the account must already be provisioned;"
                            + " it is never created here (use POST /session for that). No"
                            + " session/current-role context is returned — that is bff-owned.")
    @ApiResponse(
            responseCode = "200",
            description = "The current principal.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.CURRENT_USER)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @GetMapping("/users/me")
    public ApiEnvelope<CurrentUserResponse> me() {
        return ApiEnvelope.of(currentUser(currentUser.require()));
    }

    /**
     * POST /session — resolve or provision a login. Called once by the BFF right after the Google
     * code exchange. intent=signup provisions a new account; intent=signin requires an existing one
     * (404 otherwise → the web app sends the user to sign up). Has a write/side-effect (JIT
     * provisioning) that GET /users/me deliberately does not.
     */
    @Operation(
            summary = "Resolve a login",
            description =
                    "Resolves or provisions a login right after the Google code exchange."
                            + " intent=signup provisions a new account; intent=signin requires an"
                            + " existing one (404 otherwise, so the web app can send the user to"
                            + " sign up). Unlike the read-only GET /users/me, this endpoint may"
                            + " create the account.")
    @ApiResponse(
            responseCode = "200",
            description = "The resolved principal.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.CURRENT_USER)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid JWT principal.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "404",
            description = "intent=signin but no account is registered for this Google account.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @PostMapping("/session")
    public ApiEnvelope<CurrentUserResponse> resolveSession(
            @Parameter(
                            description =
                                    "Login intent: signup provisions a new account, signin requires"
                                            + " an existing one.")
                    @RequestParam(name = "intent", defaultValue = "signin")
                    String intent) {
        return ApiEnvelope.of(currentUser(currentUser.resolve(intent)));
    }

    /**
     * GET /users/me/role-eligibility — authoritative "can this account acquire this role" check,
     * replacing the removed {@code /userinfo.participantType} inspection. The bff calls this (not
     * any profile field) to enforce PARENT→guide during signup/onboarding routing.
     */
    @Operation(
            summary = "Role eligibility",
            description =
                    "Whether the caller may acquire the given role. GUIDE is ineligible for a"
                            + " PARENT-type participant; any role already held is ineligible"
                            + " (defensive). A disabled/suspended account is a whole-account 403,"
                            + " not eligible=false.")
    @ApiResponse(
            responseCode = "200",
            description = "The eligibility result.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = {
                                @ExampleObject(
                                        name = "eligible",
                                        value = ApiExamples.ROLE_ELIGIBILITY_ELIGIBLE),
                                @ExampleObject(
                                        name = "ineligible",
                                        value = ApiExamples.ROLE_ELIGIBILITY_INELIGIBLE)
                            }))
    @ApiResponse(
            responseCode = "400",
            description = "Missing or unrecognized 'role' query parameter.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_400)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "403",
            description =
                    "The account is not ACTIVE (disabled/suspended) — whole-account, not"
                            + " role-specific.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples =
                                    @ExampleObject(
                                            value = ApiExamples.PROBLEM_403_ACCOUNT_NOT_ACTIVE)))
    @GetMapping("/users/me/role-eligibility")
    public ApiEnvelope<RoleEligibilityResponse> roleEligibility(
            @Parameter(
                            description = "The role to check eligibility for.",
                            example = "GUIDE",
                            schema = @Schema(allowableValues = {"PARTICIPANT", "GUIDE"}))
                    @RequestParam("role")
                    String role) {
        UserEntity user = currentUser.require();
        return ApiEnvelope.of(roleEligibility.checkEligibility(user, parseRole(role)));
    }

    private static UserRole parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role is required");
        }
        try {
            return UserRole.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + raw);
        }
    }

    /** Build the principal view, enriched with the authoritative role set (fixed enum order). */
    private CurrentUserResponse currentUser(UserEntity user) {
        List<UserRole> roles =
                userRoles.findByUserId(user.getId()).stream()
                        .map(UserRoleEntity::getRole)
                        .sorted(Comparator.comparingInt(Enum::ordinal))
                        .toList();
        return CurrentUserResponse.of(user, roles);
    }
}
