package com.CampusToursLive.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.web.TourController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A HEAD on a PUBLIC path must not require authentication.
 *
 * <p>This is not tidiness; it forces a logout. The chain: the BFF's {@code isPublicGet} accepts GET
 * <em>or</em> HEAD and forwards public paths anonymously (no bearer); this config's {@code
 * permitAll} covered {@code HttpMethod.GET} only, so a HEAD fell through to {@code
 * anyRequest().authenticated()} and 401'd; and the BFF turns any Core 401 into {@code
 * requireReauth}, which calls {@code clearSession}. So one HEAD to /tours or /meta/* from a
 * signed-in user's browser — or a prefetcher, uptime probe, or scanner — destroyed their session.
 * That is exactly the class of outcome N2 exists to prevent: a session torn down for a reason that
 * says nothing about whether it is valid.
 *
 * <p>Fixed here rather than by dropping HEAD from the BFF's allowlist (the plan's original,
 * "lower-risk" suggestion). Both stop the forced logout, but dropping it leaves an anonymous HEAD
 * on a public resource answering {@code 401 + Auth-Required} — the BFF's explicit re-authenticate
 * signal — which is the wrong semantics for something anyone may read. A public resource should be
 * publicly HEAD-able.
 */
@WebMvcTest(controllers = TourController.class)
@Import(SecurityConfig.class)
class PublicHeadAccessTest {

    @Autowired private MockMvc mvc;

    /** Mocked so the filter chain builds without reaching Google's JWKS. */
    @MockitoBean private JwtDecoder jwtDecoder;

    @MockitoBean private TourDiscoveryService tourDiscoveryService;

    @Test
    void headOnPublicTourCatalogIsNotRejectedAsUnauthenticated() throws Exception {
        mvc.perform(head("/tours")).andExpect(status().is(not401()));
    }

    @Test
    void headOnPublicMetaIsNotRejectedAsUnauthenticated() throws Exception {
        // /meta/** is served by another controller; what matters here is the security decision,
        // so a 404 from routing is a pass and a 401 from the filter chain is the failure.
        mvc.perform(head("/meta/tour-topics")).andExpect(status().is(not401()));
    }

    @Test
    void getOnPublicTourCatalogStaysPublic() throws Exception {
        mvc.perform(get("/tours")).andExpect(status().is(not401()));
    }

    /** Matches any status except 401 — the filter chain's "you must authenticate" answer. */
    private static org.hamcrest.Matcher<Integer> not401() {
        return org.hamcrest.Matchers.not(401);
    }
}
