package com.CampusToursLive.config;

import com.CampusToursLive.web.idempotency.IdempotencyFilter;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class WebConfig {

    // Honor X-Forwarded-* headers from the upstream proxy/BFF so the app sees the original client
    // scheme, host, and port (needed for correct absolute URLs behind TLS termination).
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    // Idempotency filter as its OWN bean so its @Scheduled TTL sweep is registered, then attached
    // to the servlet chain via FilterRegistrationBean (which suppresses Boot's auto-registration).
    // Defined here rather than @Component so @WebMvcTest slices -- which instantiate Filter beans
    // but provide no JdbcTemplate -- don't try (and fail) to build it.
    @Bean
    public IdempotencyFilter idempotencyFilter(JdbcTemplate jdbcTemplate) {
        return new IdempotencyFilter(jdbcTemplate);
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
            IdempotencyFilter idempotencyFilter) {
        FilterRegistrationBean<IdempotencyFilter> registration =
                new FilterRegistrationBean<>(idempotencyFilter);
        // Run AFTER Spring Security's filter chain so the request is already authenticated.
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
        return registration;
    }
}
