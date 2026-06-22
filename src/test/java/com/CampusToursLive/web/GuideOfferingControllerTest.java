package com.CampusToursLive.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.tour.TourOfferingService;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.TourOfferingResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice test for the guide offerings controller: real routing, @RequestBody binding, the
 * {data, meta} envelope, and — crucially — that domain exceptions ({@link ForbiddenException},
 * {@link ValidationException}) map to RFC 7807 problem+json via {@code GlobalExceptionHandler}
 * (which the slice picks up). The security auto-configs are excluded so the test runs offline (no
 * Google JWKS fetch) and isolates the web layer; authorization here is the (mocked) {@link
 * CurrentUser}, not the filter chain.
 */
@WebMvcTest(
        controllers = GuideOfferingController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
class GuideOfferingControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private TourOfferingService offerings;

    private static UserEntity user() {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        return u;
    }

    @Test
    void list_returnsEnvelope_whenAuthorized() throws Exception {
        UserEntity u = user();
        TourOfferingResponse row =
                new TourOfferingResponse(
                        "o1", "Campus walk", null, null, null, null, null, null, null, null);
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(offerings.listOwn(u)).thenReturn(List.of(row));

        mvc.perform(get("/guide/offerings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("o1"))
                .andExpect(jsonPath("$.data[0].title").value("Campus walk"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void list_403_whenGuideRoleMissing() throws Exception {
        when(currentUser.requireRole(UserRole.GUIDE))
                .thenThrow(new ForbiddenException("Missing required role: GUIDE"));

        mvc.perform(get("/guide/offerings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Missing required role: GUIDE"));
    }

    @Test
    void activate_403_whenApplicationNotApproved() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(offerings.activate(u, id))
                .thenThrow(
                        new ForbiddenException(
                                "Your guide application must be approved before you can publish offerings"));

        mvc.perform(post("/guide/offerings/{id}/activate", id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void create_422_whenBodyInvalid() throws Exception {
        UserEntity u = user();
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(offerings.create(eq(u), any()))
                .thenThrow(new ValidationException("title is required"));

        mvc.perform(
                        post("/guide/offerings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"topic\":\"ACADEMICS\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("title is required"));
    }

    @Test
    void create_returnsEnvelope_whenValid() throws Exception {
        UserEntity u = user();
        TourOfferingResponse o =
                new TourOfferingResponse(
                        "o9",
                        "CS Lab Life",
                        "cs-lab-life",
                        "DRAFT",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(offerings.create(eq(u), any())).thenReturn(o);

        mvc.perform(
                        post("/guide/offerings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"CS Lab Life\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("o9"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void activate_returnsEnvelope_whenValid() throws Exception {
        UserEntity u = user();
        UUID id = UUID.randomUUID();
        TourOfferingResponse o =
                new TourOfferingResponse(
                        id.toString(),
                        "CS Lab Life",
                        "cs-lab-life",
                        "ACTIVE",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        when(currentUser.requireRole(UserRole.GUIDE)).thenReturn(u);
        when(offerings.activate(u, id)).thenReturn(o);

        mvc.perform(post("/guide/offerings/{id}/activate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
