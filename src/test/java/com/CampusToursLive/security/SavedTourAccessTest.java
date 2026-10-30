package com.CampusToursLive.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.saved.SavedTourService;
import com.CampusToursLive.web.SavedTourController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Saved tours are never public: every method needs a bearer token. */
@WebMvcTest(controllers = SavedTourController.class)
@Import(SecurityConfig.class)
class SavedTourAccessTest {

    private static final String BASE = "/participant/saved-tours";

    @Autowired private MockMvc mvc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private SavedTourService savedTourService;

    @Test
    void anonymousList_is401() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousIds_is401() throws Exception {
        mvc.perform(get(BASE + "/ids")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousSave_is401() throws Exception {
        mvc.perform(put(BASE + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUnsave_is401() throws Exception {
        mvc.perform(delete(BASE + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
