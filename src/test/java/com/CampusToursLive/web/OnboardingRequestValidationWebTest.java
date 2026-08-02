package com.CampusToursLive.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.web.dto.GuideOnboardingRequest;
import com.CampusToursLive.web.dto.ParticipantOnboardingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end proof that {@code @Valid @RequestBody} on the dedicated onboarding request DTOs (Task
 * 4) actually enforces bean validation and that {@link GlobalExceptionHandler} maps the result the
 * way Core's contract requires: 422 {@code VALIDATION_FAILED} for a well-formed body missing a
 * first-time-required field, 200 for a fully valid body, and 400 for a structurally malformed
 * (unparsable JSON) body. Uses a standalone MockMvc + a tiny fixture controller since the real
 * onboarding controller isn't wired until Task 7 — this only proves the DTO + handler contract.
 */
class OnboardingRequestValidationWebTest {

    @RestController
    static class FixtureController {
        @PostMapping("/__test/guide-onboarding")
        public GuideOnboardingRequest guide(@Valid @RequestBody GuideOnboardingRequest req) {
            return req;
        }

        @PostMapping("/__test/participant-onboarding")
        public ParticipantOnboardingRequest participant(
                @Valid @RequestBody ParticipantOnboardingRequest req) {
            return req;
        }
    }

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new FixtureController())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void guideOnboarding_missingRequiredField_yields422ValidationFailed() throws Exception {
        String body =
                """
                {
                  "major": "Marine Biology",
                  "degree": "Bachelor's Degree",
                  "bio": "Third-year student and campus tour lead.",
                  "tourTopics": ["DORM_HOUSING"],
                  "verificationEmail": "maya.chen@ncu.edu"
                }
                """;

        mockMvc.perform(
                        post("/__test/guide-onboarding")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void guideOnboarding_validRequest_yields200() throws Exception {
        var req =
                new GuideOnboardingRequest(
                        "Maya",
                        "Chen",
                        "u1a2c3d4-0000-4000-8000-000000000003",
                        "Marine Biology",
                        "2027",
                        "Third-year student and campus tour lead.",
                        java.util.List.of("en-US"),
                        java.util.List.of("DORM_HOUSING"),
                        "maya.chen@ncu.edu",
                        "Bachelor's Degree",
                        2023);

        mockMvc.perform(
                        post("/__test/guide-onboarding")
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void guideOnboarding_malformedJson_yields400() throws Exception {
        mockMvc.perform(
                        post("/__test/guide-onboarding")
                                .contentType("application/json")
                                .content("{ this is not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void participantOnboarding_missingParticipantType_yields422ValidationFailed() throws Exception {
        String body = "{ \"firstName\": \"Sam\" }";

        mockMvc.perform(
                        post("/__test/participant-onboarding")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void participantOnboarding_validRequest_yields200() throws Exception {
        var req =
                new ParticipantOnboardingRequest(
                        "Sam",
                        "Rivera",
                        "Sam Rivera",
                        "PROSPECTIVE",
                        "High school senior",
                        "Computer Science",
                        java.util.List.of("166683"),
                        java.util.List.of("DORM_HOUSING"),
                        "en-US",
                        "America/New_York",
                        null);

        mockMvc.perform(
                        post("/__test/participant-onboarding")
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ---- topicsOfInterest controlled vocabulary (CTL-97 Option B: onboarding-only) ---------

    @Test
    void participantOnboarding_validTopicsSubset_yields200() throws Exception {
        String body =
                """
                {
                  "participantType": "PROSPECTIVE",
                  "topicsOfInterest": ["DORM_HOUSING", "FRESHMAN"]
                }
                """;

        mockMvc.perform(
                        post("/__test/participant-onboarding")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void participantOnboarding_invalidTopicCode_yields422ValidationFailed() throws Exception {
        String body =
                """
                {
                  "participantType": "PROSPECTIVE",
                  "topicsOfInterest": ["NOT_A_TOPIC"]
                }
                """;

        mockMvc.perform(
                        post("/__test/participant-onboarding")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void participantOnboarding_lowercaseTopicCode_yields422ValidationFailed() throws Exception {
        String body =
                """
                {
                  "participantType": "PROSPECTIVE",
                  "topicsOfInterest": ["dorm-life"]
                }
                """;

        mockMvc.perform(
                        post("/__test/participant-onboarding")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void participantOnboarding_emptyTopicsList_yields200() throws Exception {
        String body =
                """
                {
                  "participantType": "PROSPECTIVE",
                  "topicsOfInterest": []
                }
                """;

        mockMvc.perform(
                        post("/__test/participant-onboarding")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void participantOnboarding_omittedTopics_yields200() throws Exception {
        String body =
                """
                {
                  "participantType": "PROSPECTIVE"
                }
                """;

        mockMvc.perform(
                        post("/__test/participant-onboarding")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void participantOnboarding_duplicateValidTopicCodes_yields200() throws Exception {
        String body =
                """
                {
                  "participantType": "PROSPECTIVE",
                  "topicsOfInterest": ["DORM_HOUSING", "DORM_HOUSING"]
                }
                """;

        mockMvc.perform(
                        post("/__test/participant-onboarding")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk());
    }
}
