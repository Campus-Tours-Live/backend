package com.CampusToursLive.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.onboarding.OnboardingService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.OnboardingResponse;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.UserSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web-layer slice test for {@link OnboardingController}: real routing, {@code @Valid @RequestBody}
 * binding, the {data, meta} envelope at 201, and that {@link OnboardingService}'s coded exceptions
 * map to RFC 7807 problem+json via {@code GlobalExceptionHandler}. Mirrors {@code
 * AvailabilityControllerTest}: {@link OnboardingService} is mocked, so this only exercises the
 * controller/HTTP-status wiring, never real persistence (covered separately by {@code
 * OnboardingServiceIntegrationTest}).
 *
 * <p>Unlike every other controller slice in this codebase, there is no {@code CurrentUser} gate to
 * mock here at all — that is the point of Task 7 (I11): the controller never pre-rejects a PENDING
 * caller, it forwards straight to {@link OnboardingService}, which is free to provision-if-absent.
 * {@code onboard*_pendingIdentity_reachesService_returns201} below documents exactly that: the mock
 * simply succeeds (a stand-in for "OnboardingService accepted a PENDING identity"), and the
 * assertion is that the controller does not short-circuit with a 404/401 first.
 */
@WebMvcTest(
        controllers = OnboardingController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
class OnboardingControllerTest {

    /**
     * {@code @WebMvcTest} slices in this repo exclude {@code SecurityAutoConfiguration} / {@code
     * OAuth2ResourceServerAutoConfiguration} (see the class annotation below) so no real JWT
     * decoding infra has to be stood up per-slice. That also means Spring Security's own
     * {@code @EnableWebSecurity} never fires, so the {@code @AuthenticationPrincipal} argument
     * resolver this controller relies on is never auto-registered — a plain {@code @Bean
     * HandlerMethodArgumentResolver} is not enough either, since nothing in this slice consumes
     * that bean type on its own. Implementing {@link WebMvcConfigurer#addArgumentResolvers} here is
     * what actually wires it into the adapter, restoring exactly the behavior production gets from
     * the real security filter chain, without needing the rest of it.
     */
    @TestConfiguration
    static class AuthenticationPrincipalTestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired private MockMvc mvc;

    @MockitoBean private OnboardingService onboardingService;

    private static final String USER_ID = "22222222-0000-4000-8000-000000000001";

    private static UserSummary userSummary() {
        return new UserSummary(
                USER_ID,
                "Maya",
                "Chen",
                "Maya Chen",
                "maya.chen@example.com",
                "ACTIVE",
                "ADULT",
                "2026-07-24T09:30:00Z");
    }

    private static OnboardingResponse guideOnboardingResponse() {
        GuideProfileResponse profile =
                new GuideProfileResponse(
                        "PENDING",
                        List.of(),
                        "Third-year student and campus tour lead.",
                        List.of("en-US"),
                        List.of("DORM_HOUSING"));
        return new OnboardingResponse(
                userSummary(), List.of(UserRole.GUIDE), UserRole.GUIDE, profile);
    }

    private static OnboardingResponse participantOnboardingResponse() {
        ParticipantProfileResponse profile =
                new ParticipantProfileResponse(
                        "PENDING",
                        "PROSPECTIVE",
                        null,
                        null,
                        false,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null);
        return new OnboardingResponse(
                userSummary(), List.of(UserRole.PARTICIPANT), UserRole.PARTICIPANT, profile);
    }

    private static String guideBody() {
        return """
                {
                  "firstName": "Maya",
                  "lastName": "Chen",
                  "universityId": "u1a2c3d4-0000-4000-8000-000000000003",
                  "major": "Marine Biology",
                  "bio": "Third-year student and campus tour lead.",
                  "tourTopics": ["DORM_HOUSING"],
                  "verificationEmail": "maya.chen@ncu.edu",
                  "degree": "Bachelor's Degree"
                }
                """;
    }

    private static String guideBodyMissingUniversityId() {
        return """
                {
                  "major": "Marine Biology",
                  "degree": "Bachelor's Degree",
                  "bio": "Third-year student and campus tour lead.",
                  "tourTopics": ["DORM_HOUSING"],
                  "verificationEmail": "maya.chen@ncu.edu"
                }
                """;
    }

    private static String participantBody() {
        return "{ \"participantType\": \"PROSPECTIVE\" }";
    }

    // ---------------------------------------------------------------------
    // POST /users/me/roles/guide
    // ---------------------------------------------------------------------

    @Test
    void onboardGuide_roleNotHeld_returns201WithoutCurrentRole() throws Exception {
        when(onboardingService.onboardGuide(any(), any())).thenReturn(guideOnboardingResponse());

        mvc.perform(
                        post("/users/me/roles/guide")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(guideBody())
                                .with(jwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountState").doesNotExist())
                .andExpect(jsonPath("$.data.user.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.id").value(USER_ID))
                .andExpect(jsonPath("$.data.roles[0]").value("GUIDE"))
                .andExpect(jsonPath("$.data.acquiredRole").value("GUIDE"))
                .andExpect(jsonPath("$.data.profile.guideStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.currentRole").doesNotExist())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void onboardGuide_roleAlreadyHeld_returns409RoleAlreadyGranted() throws Exception {
        when(onboardingService.onboardGuide(any(), any()))
                .thenThrow(ConflictException.roleAlreadyGranted("GUIDE"));

        mvc.perform(
                        post("/users/me/roles/guide")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(guideBody())
                                .with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ALREADY_GRANTED"))
                .andExpect(jsonPath("$.reconciliationRequired").value(true));
    }

    @Test
    void onboardGuide_parentParticipant_returns409RoleNotEligible() throws Exception {
        when(onboardingService.onboardGuide(any(), any()))
                .thenThrow(ConflictException.roleNotEligible("GUIDE"));

        mvc.perform(
                        post("/users/me/roles/guide")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(guideBody())
                                .with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ELIGIBLE"));
    }

    @Test
    void onboardGuide_missingRequiredField_returns422() throws Exception {
        mvc.perform(
                        post("/users/me/roles/guide")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(guideBodyMissingUniversityId())
                                .with(jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void onboardGuide_pendingIdentity_reachesService_returns201() throws Exception {
        // A PENDING identity (no `users` row yet) must NOT be pre-rejected by the controller (no
        // 404/401 short-circuit) -- the request must reach OnboardingService, which does its own
        // provision-if-absent under the identity lock. The mock succeeding stands in for that.
        when(onboardingService.onboardGuide(any(), any())).thenReturn(guideOnboardingResponse());

        mvc.perform(
                        post("/users/me/roles/guide")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(guideBody())
                                .with(jwt()))
                .andExpect(status().isCreated());

        verify(onboardingService).onboardGuide(any(), any());
    }

    // ---------------------------------------------------------------------
    // POST /users/me/roles/participant
    // ---------------------------------------------------------------------

    @Test
    void onboardParticipant_roleNotHeld_returns201WithoutCurrentRole() throws Exception {
        when(onboardingService.onboardParticipant(any(), any()))
                .thenReturn(participantOnboardingResponse());

        mvc.perform(
                        post("/users/me/roles/participant")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(participantBody())
                                .with(jwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountState").doesNotExist())
                .andExpect(jsonPath("$.data.user.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.id").value(USER_ID))
                .andExpect(jsonPath("$.data.roles[0]").value("PARTICIPANT"))
                .andExpect(jsonPath("$.data.acquiredRole").value("PARTICIPANT"))
                .andExpect(jsonPath("$.data.profile.participantStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.currentRole").doesNotExist())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void onboardParticipant_roleAlreadyHeld_returns409RoleAlreadyGranted() throws Exception {
        when(onboardingService.onboardParticipant(any(), any()))
                .thenThrow(ConflictException.roleAlreadyGranted("PARTICIPANT"));

        mvc.perform(
                        post("/users/me/roles/participant")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(participantBody())
                                .with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ALREADY_GRANTED"))
                .andExpect(jsonPath("$.reconciliationRequired").value(true));
    }

    @Test
    void onboardParticipant_parentWhileGuide_returns409RoleNotEligible() throws Exception {
        when(onboardingService.onboardParticipant(any(), any()))
                .thenThrow(ConflictException.roleNotEligible("PARTICIPANT"));

        mvc.perform(
                        post("/users/me/roles/participant")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"participantType\": \"PARENT\" }")
                                .with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ELIGIBLE"));
    }

    @Test
    void onboardParticipant_missingRequiredField_returns422() throws Exception {
        mvc.perform(
                        post("/users/me/roles/participant")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .with(jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void onboardParticipant_pendingIdentity_reachesService_returns201() throws Exception {
        when(onboardingService.onboardParticipant(any(), any()))
                .thenReturn(participantOnboardingResponse());

        mvc.perform(
                        post("/users/me/roles/participant")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(participantBody())
                                .with(jwt()))
                .andExpect(status().isCreated());

        verify(onboardingService).onboardParticipant(any(), any());
    }
}
