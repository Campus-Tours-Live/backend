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
 * Stateless security. Every request is authenticated by a Google OIDC id_token (JWT), validated
 * against Google's JWKS (signature + issuer + expiry) and, when configured, the audience (our
 * Google Client ID). There is no local stub.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/health",
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll()
                                        // Public marketplace: the tour catalog + reference-data
                                        // lookups are readable without a session.
                                        //
                                        // HEAD is listed alongside GET deliberately (L5#2). Without
                                        // it a HEAD fell through to anyRequest().authenticated()
                                        // and
                                        // 401'd -- and because the BFF forwards public paths
                                        // anonymously and turns any Core 401 into requireReauth ->
                                        // clearSession, a single HEAD from a signed-in user's
                                        // browser, a prefetcher or an uptime probe DESTROYED their
                                        // session. A publicly readable resource must be publicly
                                        // HEAD-able; anything else makes a metadata request a
                                        // logout.
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/tours",
                                                "/tours/**",
                                                "/meta/**")
                                        .permitAll()
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.HEAD,
                                                "/tours",
                                                "/tours/**",
                                                "/meta/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * JWT decoder for the issuer's JWKS, plus an audience check so only id_tokens minted for this
     * app's Google Client ID are accepted.
     *
     * <p>Fails fast when {@code app.auth.audience} (the Google Client ID) is blank, so a
     * misconfigured staging/prod never boots silently accepting any Google id_token. Local dev
     * without a Client ID must opt in explicitly with {@code app.auth.allow-insecure-audience=true}
     * ({@code ALLOW_INSECURE_AUDIENCE=true}).
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${app.auth.audience:}") String audience,
            @Value("${app.auth.allow-insecure-audience:false}") boolean allowInsecure) {
        // Build (and validate) the token validator BEFORE fetching the issuer's JWKS, so a blank
        // audience fails startup immediately rather than after a network round-trip.
        OAuth2TokenValidator<Jwt> validator = tokenValidator(issuerUri, audience, allowInsecure);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Chooses the JWT validator and enforces the audience fail-fast. Package-private so it can be
     * unit-tested without a Spring context or network (the JWKS fetch lives in {@link
     * #jwtDecoder}).
     *
     * @throws IllegalStateException when {@code audience} is blank and the insecure opt-out is off.
     */
    static OAuth2TokenValidator<Jwt> tokenValidator(
            String issuerUri, String audience, boolean allowInsecure) {
        if (audience.isBlank() && !allowInsecure) {
            throw new IllegalStateException(
                    "app.auth.audience (GOOGLE_CLIENT_ID) is blank. Set it, or explicitly set "
                            + "app.auth.allow-insecure-audience=true for local dev (would accept any"
                            + " Google id_token).");
        }
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        return audience.isBlank()
                ? withIssuer
                : new DelegatingOAuth2TokenValidator<>(withIssuer, new AudienceValidator(audience));
    }
}
