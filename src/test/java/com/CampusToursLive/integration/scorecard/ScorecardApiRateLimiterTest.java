package com.CampusToursLive.integration.scorecard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Layer 2. This exercises the real {@code @RateLimiter} annotation through a Spring proxy — not the
 * fallback methods in isolation — because the failure mode we are guarding against is precisely an
 * annotation that silently never applies (which is what a self-invocation inside a single bean
 * would give us).
 *
 * <p>The assertion that matters is the outbound call COUNT: once the limiter is exhausted the HTTP
 * call must not happen at all, and the caller must still get the degraded empty value rather than a
 * RequestNotPermittedException.
 */
class ScorecardApiRateLimiterTest {

    private static final String ONE_RESULT =
            """
            {"results":[{"id":243744,"school.name":"Stanford University",
             "school.city":"Stanford","school.state":"CA"}]}
            """;

    /** Resilience4j's aspect is an AOP proxy; the slice needs auto-proxying switched on. */
    @Configuration
    @EnableAspectJAutoProxy
    static class AopConfig {}

    /** A RestClient that never leaves the JVM: it counts calls and returns a canned payload. */
    private static RestClient countingClient(AtomicInteger calls) {
        return RestClient.builder()
                .baseUrl("https://scorecard.test/v1")
                .requestInterceptor(
                        (request, body, execution) -> {
                            calls.incrementAndGet();
                            MockClientHttpResponse response =
                                    new MockClientHttpResponse(
                                            ONE_RESULT.getBytes(UTF_8), HttpStatus.OK);
                            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            return response;
                        })
                .build();
    }

    private ApplicationContextRunner runnerWithLimitOf(int permits, AtomicInteger calls) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RateLimiterAutoConfiguration.class))
                .withUserConfiguration(AopConfig.class)
                .withPropertyValues(
                        "resilience4j.ratelimiter.instances.scorecard.limit-for-period=" + permits,
                        "resilience4j.ratelimiter.instances.scorecard.limit-refresh-period=1h",
                        "resilience4j.ratelimiter.instances.scorecard.timeout-duration=0")
                .withBean(ScorecardApi.class, () -> new ScorecardApi(countingClient(calls), "KEY"));
    }

    @Test
    void exhaustedLimiterDegradesToEmptyAndSuppressesTheOutboundCall() {
        AtomicInteger calls = new AtomicInteger();

        runnerWithLimitOf(1, calls)
                .run(
                        context -> {
                            ScorecardApi api = context.getBean(ScorecardApi.class);

                            // First call gets the only permit and really goes out.
                            assertThat(api.searchSchools("stanford", 5)).hasSize(1);
                            assertThat(calls.get()).isEqualTo(1);

                            // Second call has no permit: fallback, no throw, and — the point of
                            // Layer 2 — no outbound request against the shared API key.
                            assertThat(api.searchSchools("berkeley", 5)).isEmpty();
                            assertThat(calls.get()).isEqualTo(1);
                        });
    }

    @Test
    void everyOutboundMethodIsBehindTheSameLimiter() {
        AtomicInteger calls = new AtomicInteger();

        // Two permits, three outbound methods → the third is refused whichever order they run in.
        runnerWithLimitOf(2, calls)
                .run(
                        context -> {
                            ScorecardApi api = context.getBean(ScorecardApi.class);

                            assertThat(api.getSchool("243744")).isNotNull();
                            assertThat(api.majorsForSchool("243744")).isEmpty(); // no programs node
                            assertThat(calls.get()).isEqualTo(2);

                            // Permits exhausted: degraded null, no throw, no outbound call.
                            assertThat(api.getSchool("243744")).isNull();
                            assertThat(calls.get()).isEqualTo(2);
                        });
    }

    /**
     * Guards the shipped limiter configuration itself. 800/h sits under the api.data.gov default
     * quota of 1000/h for the shared key, and timeout-duration MUST stay 0 — a non-zero timeout
     * would park Tomcat request threads waiting for a permit and turn a quota problem into an
     * outage.
     */
    @Test
    void applicationPropertiesShipTheExpectedLimiterConfig() throws IOException {
        Properties props = new Properties();
        try (var in = new ClassPathResource("application.properties").getInputStream()) {
            props.load(in);
        }

        assertThat(
                        props.getProperty(
                                "resilience4j.ratelimiter.instances.scorecard.limit-for-period"))
                .isEqualTo("800");
        assertThat(
                        props.getProperty(
                                "resilience4j.ratelimiter.instances.scorecard.limit-refresh-period"))
                .isEqualTo("1h");
        assertThat(
                        props.getProperty(
                                "resilience4j.ratelimiter.instances.scorecard.timeout-duration"))
                .isEqualTo("0");
    }
}
