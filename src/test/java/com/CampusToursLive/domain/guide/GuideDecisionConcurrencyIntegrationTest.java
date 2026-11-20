package com.CampusToursLive.domain.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A bio-only profile save that started while the guide was PENDING must not write guide_status or
 * approved_at back over an admin decision that committed in between.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GuideDecisionConcurrencyIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager txManager;

    @Test
    void bioEditAfterApprovalKeepsVerifiedStatus() throws Exception {
        UUID profileId = seedPendingGuide();
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch approved = new CountDownLatch(1);
        AtomicReference<Throwable> editError = new AtomicReference<>();

        Thread editor =
                new Thread(
                        () -> {
                            try {
                                new TransactionTemplate(txManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    GuideProfileEntity profile =
                                                            guides.findById(profileId)
                                                                    .orElseThrow();
                                                    loaded.countDown();
                                                    await(approved);
                                                    profile.setBio("updated bio");
                                                    guides.saveAndFlush(profile);
                                                });
                            } catch (Throwable t) {
                                editError.set(t);
                                loaded.countDown();
                            }
                        },
                        "guide-bio-edit");
        editor.start();
        try {
            assertTrue(loaded.await(10, TimeUnit.SECONDS), "editor never loaded the profile");
            new TransactionTemplate(txManager)
                    .executeWithoutResult(
                            status ->
                                    assertEquals(
                                            1,
                                            guides.approveIfPending(
                                                    profileId,
                                                    Instant.parse("2026-10-29T00:00:00Z"))));
            approved.countDown();
            editor.join(15_000);
        } finally {
            approved.countDown();
        }

        assertNull(editError.get());
        GuideProfileEntity row =
                new TransactionTemplate(txManager)
                        .execute(status -> guides.findById(profileId).orElseThrow());
        assertEquals(GuideStatus.VERIFIED, row.getStatus());
        assertEquals("updated bio", row.getBio());
        assertEquals(Instant.parse("2026-10-29T00:00:00Z"), row.getApprovedAt());
    }

    private UUID seedPendingGuide() {
        return new TransactionTemplate(txManager)
                .execute(
                        status -> {
                            UserEntity user = new UserEntity();
                            user.setId(UUID.randomUUID());
                            user.setOidcSubject("decision-race-" + UUID.randomUUID());
                            user.setEmail("decision-race-" + UUID.randomUUID() + "@example.com");
                            user.setDisplayName("Decision Race Guide");
                            user.setAccountStatus(AccountStatus.ACTIVE);
                            user.setPreferredLanguage("en-US");
                            user.setTimezone("America/Los_Angeles");
                            users.saveAndFlush(user);

                            GuideProfileEntity profile = new GuideProfileEntity();
                            profile.setId(UUID.randomUUID());
                            profile.setUserId(user.getId());
                            profile.setStatus(GuideStatus.PENDING);
                            return guides.saveAndFlush(profile).getId();
                        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for the approval to commit");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
