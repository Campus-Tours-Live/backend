package com.CampusToursLive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class WebConfig {

    // Honor X-Forwarded-* headers from the upstream proxy/BFF so the app sees the original client
    // scheme, host, and port (needed for correct absolute URLs behind TLS termination).
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
