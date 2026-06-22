package com.CampusToursLive.web;

import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.user.ActiveRoleService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ActiveRoleRequest;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.MeResponse;
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
public class SessionController {

    private final CurrentUser currentUser;
    private final UserRoleRepository userRoles;
    private final ParticipantProfileRepository participants;
    private final GuideProfileRepository guides;
    private final ActiveRoleService activeRole;

    public SessionController(
            CurrentUser currentUser,
            UserRoleRepository userRoles,
            ParticipantProfileRepository participants,
            GuideProfileRepository guides,
            ActiveRoleService activeRole) {
        this.currentUser = currentUser;
        this.userRoles = userRoles;
        this.participants = participants;
        this.guides = guides;
        this.activeRole = activeRole;
    }

    /** GET /userinfo — the current authenticated principal (must be provisioned). */
    @GetMapping("/userinfo")
    public ApiEnvelope<MeResponse> userinfo() {
        return ApiEnvelope.of(me(currentUser.require()));
    }

    /**
     * POST /session — resolve a login. Called once by the BFF right after the Google code exchange.
     * intent=signup provisions a new account; intent=signin requires an existing one (404 otherwise
     * → the web app sends the user to sign up).
     */
    @PostMapping("/session")
    public ApiEnvelope<MeResponse> resolveSession(
            @RequestParam(name = "intent", defaultValue = "signin") String intent) {
        return ApiEnvelope.of(me(currentUser.resolve(intent)));
    }

    /**
     * POST /session/active-role — switch the caller's active role (UX context only; authorization
     * always reads user_roles). Pure Core: updates last_active_role, never touches the cookie. Only
     * a held, switchable role is allowed.
     */
    @PostMapping("/session/active-role")
    public ApiEnvelope<MeResponse> setActiveRole(@RequestBody ActiveRoleRequest req) {
        UserEntity user = currentUser.require();
        activeRole.switchActiveRole(user, req.role());
        return ApiEnvelope.of(me(user));
    }

    /** Build the principal view, enriched with the authoritative role set + per-role status. */
    private MeResponse me(UserEntity user) {
        List<String> roles =
                userRoles.findByUserId(user.getId()).stream()
                        .map(ur -> ur.getRole().name())
                        .sorted()
                        .toList();
        String participantType =
                participants
                        .findByUserId(user.getId())
                        .map(
                                p ->
                                        p.getParticipantType() != null
                                                ? p.getParticipantType().name()
                                                : null)
                        .orElse(null);
        String guideStatus =
                guides.findByUserId(user.getId())
                        .map(
                                g ->
                                        g.getApplicationStatus() != null
                                                ? g.getApplicationStatus().name()
                                                : null)
                        .orElse(null);
        return MeResponse.of(user, roles, participantType, guideStatus);
    }
}
