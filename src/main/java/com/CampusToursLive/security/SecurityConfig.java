package com.CampusToursLive.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security. Authenticated requests carry a Google OIDC id_token (JWT), validated against
 * Google's JWKS (signature + issuer + expiry) plus the audience (our Google Client ID, which is
 * required — see {@link #tokenValidator}). There is no local stub.
 *
 * <p>Not everything is authenticated: the public marketplace (GET/HEAD on {@code /tours}, {@code
 * /tours/**}, {@code /meta/**}) plus health and the API docs are served anonymously. The matcher
 * list below is the authoritative statement of that surface.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // 1. Basic security configuration
        http.csrf(AbstractHttpConfigurer::disable);

        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 2. Authorization rules
        http.authorizeHttpRequests(
                auth ->
                        auth

                                // Health + API documentation
                                .requestMatchers(
                                        "/health",
                                        "/actuator/health",
                                        "/actuator/health/**",
                                        "/v3/api-docs/**",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html")
                                .permitAll()

                                // Public GET APIs
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/tours",
                                        "/tours/**",
                                        "/meta/**",
                                        "/universities",
                                        "/universities/**")
                                .permitAll()

                                // Public HEAD APIs
                                .requestMatchers(
                                        HttpMethod.HEAD,
                                        "/tours",
                                        "/tours/**",
                                        "/meta/**",
                                        "/universities",
                                        "/universities/**")
                                .permitAll()

                                // Everything else requires login
                                .anyRequest()
                                .authenticated());

        // 3. JWT authentication
        http.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

        // 4. Build the filter chain
        return http.build();
    }

    /**
     * JWT decoder for the issuer's JWKS, plus an audience check so only id_tokens minted for this
     * app's Google Client ID are accepted.
     *
     * <p>Fails fast when {@code app.auth.audience} (the Google Client ID) is blank. There is no
     * opt-out: without an audience the service would accept any validly-signed Google id_token,
     * meaning any Google account on the internet could be provisioned here as a user. That is an
     * authentication bypass, not a local-dev convenience, so it is not reachable by omission.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${app.auth.audience:}") String audience) {
        // Build (and validate) the token validator BEFORE fetching the issuer's JWKS, so a blank
        // audience fails startup immediately rather than after a network round-trip.
        OAuth2TokenValidator<Jwt> validator = tokenValidator(issuerUri, audience);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Chooses the JWT validator and enforces the audience fail-fast. Package-private so it can be
     * unit-tested without a Spring context or network (the JWKS fetch lives in {@link
     * #jwtDecoder}).
     *
     * @throws IllegalStateException when {@code audience} is blank.
     */
    static OAuth2TokenValidator<Jwt> tokenValidator(String issuerUri, String audience) {
        if (audience.isBlank()) {
            throw new IllegalStateException(
                    "app.auth.audience is blank — set GOOGLE_CLIENT_ID. Without it every"
                            + " validly-signed Google id_token would be accepted, whoever it was"
                            + " issued for.");
        }
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri), new AudienceValidator(audience));
    }
}
