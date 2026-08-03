package com.CampusToursLive.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The ONE clock behind every year-derived value (spec I2). UTC, not the JVM default zone: the
 * enrollment-year window and the cache lifetime that expires with it must roll over at the same
 * instant, and a deployment east of UTC would otherwise turn the year over hours before the cached
 * rules expired — leaving clients validating against last year's window for exactly that long.
 *
 * <p>Injecting it (rather than calling {@code Year.now()}) is also what makes the rollover
 * behaviour testable: an ambient clock cannot be moved to 31 December.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
