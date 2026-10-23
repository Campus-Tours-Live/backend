package com.CampusToursLive.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.guide.GuideDecision;
import com.CampusToursLive.domain.guide.GuideDecisionService;
import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.security.CurrentUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminGuideController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
class AdminGuideControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private GuideDecisionService decisions;

    private final UUID adminId = UUID.randomUUID();
    private final UUID guideUserId = UUID.randomUUID();

    private UserEntity admin() {
        UserEntity u = new UserEntity();
        u.setId(adminId);
        return u;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder decide(
            String body) {
        return post("/admin/guides/{userId}/decision", guideUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void approve_returnsEnvelope() throws Exception {
        when(currentUser.requireRole(UserRole.ADMIN)).thenReturn(admin());
        when(decisions.decide(adminId, guideUserId, GuideDecision.APPROVE, null))
                .thenReturn(GuideStatus.VERIFIED);

        mvc.perform(decide("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(guideUserId.toString()))
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void reject_returnsEnvelope() throws Exception {
        when(currentUser.requireRole(UserRole.ADMIN)).thenReturn(admin());
        when(decisions.decide(adminId, guideUserId, GuideDecision.REJECT, "fake email"))
                .thenReturn(GuideStatus.REJECTED);

        mvc.perform(decide("{\"decision\":\"REJECT\",\"reason\":\"fake email\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void missingAdmin_is403() throws Exception {
        when(currentUser.requireRole(UserRole.ADMIN))
                .thenThrow(new ForbiddenException("Missing required role: ADMIN", "ROLE_REQUIRED"));

        mvc.perform(decide("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_REQUIRED"));
    }

    @Test
    void pendingCaller_is404() throws Exception {
        when(currentUser.requireRole(UserRole.ADMIN))
                .thenThrow(
                        new NotFoundException(
                                "Account not provisioned", "ACCOUNT_NOT_PROVISIONED"));

        mvc.perform(decide("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_PROVISIONED"));
    }

    @Test
    void unknownGuide_is404() throws Exception {
        when(currentUser.requireRole(UserRole.ADMIN)).thenReturn(admin());
        when(decisions.decide(eq(adminId), eq(guideUserId), eq(GuideDecision.APPROVE), isNull()))
                .thenThrow(new NotFoundException("Guide not found"));

        mvc.perform(decide("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Guide not found"));
    }

    @Test
    void notPending_is409() throws Exception {
        when(currentUser.requireRole(UserRole.ADMIN)).thenReturn(admin());
        when(decisions.decide(any(), any(), any(), any()))
                .thenThrow(ConflictException.guideApplicationNotPending("VERIFIED"));

        mvc.perform(decide("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUIDE_APPLICATION_NOT_PENDING"))
                .andExpect(jsonPath("$.currentStatus").value("VERIFIED"));
    }

    @Test
    void rejectWithoutReason_is422() throws Exception {
        when(currentUser.requireRole(UserRole.ADMIN)).thenReturn(admin());
        when(decisions.decide(adminId, guideUserId, GuideDecision.REJECT, null))
                .thenThrow(
                        new ValidationException(
                                "A reason is required to reject a guide application"));

        mvc.perform(decide("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void missingDecision_is422() throws Exception {
        mvc.perform(decide("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.decision").exists());
    }

    @Test
    void reasonTooLong_is422() throws Exception {
        String reason = "x".repeat(501);
        mvc.perform(decide("{\"decision\":\"REJECT\",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.reason").exists());
    }
}
