package com.CampusToursLive.web;

import com.CampusToursLive.domain.user.ActiveRoleService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ActiveRoleRequest;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.MeResponse;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session / identity endpoints. Paths are bare (no /v1) — the BFF strips the /v1 prefix before
 * calling Core, and calls /session directly at login time.
 */
@RestController
@Tag(
        name = "Session",
        description =
                "Session / identity. Resolves a login, returns the current principal, and switches"
                        + " the active (UX-context) role.")
public class SessionController {

    private final CurrentUser currentUser;
    private final UserRoleRepository userRoles;
    private final ActiveRoleService activeRole;

    public SessionController(
            CurrentUser currentUser, UserRoleRepository userRoles, ActiveRoleService activeRole) {
        this.currentUser = currentUser;
        this.userRoles = userRoles;
        this.activeRole = activeRole;
    }

    /** GET /userinfo — the current authenticated principal (must be provisioned). */
    @Operation(
            summary = "Current principal",
            description =
                    "Returns the current authenticated principal (identity and roles). The"
                            + " account must already be provisioned.")
    @ApiResponse(
            responseCode = "200",
            description = "The current principal.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.ME)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @GetMapping("/userinfo")
    public ApiEnvelope<MeResponse> userinfo() {
        return ApiEnvelope.of(me(currentUser.require()));
    }

    /**
     * POST /session — resolve a login. Called once by the BFF right after the Google code exchange.
     * intent=signup provisions a new account; intent=signin requires an existing one (404 otherwise
     * → the web app sends the user to sign up).
     */
    @Operation(
            summary = "Resolve a login",
            description =
                    "Resolves a login right after the Google code exchange. intent=signup"
                            + " provisions a new account; intent=signin requires an existing one"
                            + " (404 otherwise, so the web app can send the user to sign up).")
    @ApiResponse(
            responseCode = "200",
            description = "The resolved principal.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.ME)))
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
    public ApiEnvelope<MeResponse> resolveSession(
            @Parameter(
                            description =
                                    "Login intent: signup provisions a new account, signin requires"
                                            + " an existing one.")
                    @RequestParam(name = "intent", defaultValue = "signin")
                    String intent) {
        return ApiEnvelope.of(me(currentUser.resolve(intent)));
    }

    /**
     * POST /session/active-role — switch the caller's active role (UX context only; authorization
     * always reads user_roles). Pure Core: updates last_active_role, never touches the cookie. Only
     * a held, switchable role is allowed.
     */
    @Operation(
            summary = "Switch active role",
            description =
                    "Switches the caller's active role (UX context only — authorization always"
                            + " reads user_roles). Only a role the caller holds and that is"
                            + " switchable (PARTICIPANT/GUIDE) is allowed.")
    @ApiResponse(
            responseCode = "200",
            description = "The principal with the new active role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.ME)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "422",
            description = "The requested role is not held or is not switchable.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/session/active-role")
    public ApiEnvelope<MeResponse> setActiveRole(@RequestBody ActiveRoleRequest req) {
        UserEntity user = currentUser.require();
        activeRole.switchActiveRole(user, req.role());
        return ApiEnvelope.of(me(user));
    }

    /** Build the principal view, enriched with the authoritative role set. */
    private MeResponse me(UserEntity user) {
        List<String> roles =
                userRoles.findByUserId(user.getId()).stream()
                        .map(ur -> ur.getRole().name())
                        .sorted()
                        .toList();
        return MeResponse.of(user, roles);
    }
}
