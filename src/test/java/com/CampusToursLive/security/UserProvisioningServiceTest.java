package com.CampusToursLive.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.AgeBand;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * UserProvisioningService — onboarding-time creation of a BARE account from a Google id_token (no
 * role, no profile; those are written by the same onboarding transaction, see OnboardingService).
 * Covers the display-name fallback chain and the fixed account defaults.
 */
@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @Mock UserRepository users;

    private UserProvisioningService service() {
        return new UserProvisioningService(users);
    }

    private static Jwt.Builder base() {
        return Jwt.withTokenValue("t").header("alg", "none").subject("google-sub-1");
    }

    @Test
    void usesNameClaim_whenPresent() {
        Jwt jwt =
                base().claim("name", "Jordan Lee")
                        .claim("given_name", "Jordan")
                        .claim("family_name", "Lee")
                        .claim("email", "jordan@example.com")
                        .build();
        UserEntity u = service().createUserForOnboarding(jwt);
        assertEquals("Jordan Lee", u.getDisplayName());
    }

    @Test
    void fallsBackToGivenPlusFamily_whenNoNameClaim() {
        Jwt jwt = base().claim("given_name", "Jordan").claim("family_name", "Lee").build();
        UserEntity u = service().createUserForOnboarding(jwt);
        assertEquals("Jordan Lee", u.getDisplayName());
    }

    @Test
    void fallsBackToFamilyOnly_trimmed_whenGivenMissing() {
        Jwt jwt = base().claim("family_name", "Lee").build();
        UserEntity u = service().createUserForOnboarding(jwt);
        assertEquals("Lee", u.getDisplayName());
    }

    @Test
    void fallsBackToEmail_whenNoNameParts() {
        Jwt jwt = base().claim("email", "jordan@example.com").build();
        UserEntity u = service().createUserForOnboarding(jwt);
        assertEquals("jordan@example.com", u.getDisplayName());
    }

    @Test
    void fallsBackToNewUser_whenNothingIdentifying() {
        UserEntity u = service().createUserForOnboarding(base().build());
        assertEquals("New user", u.getDisplayName());
    }

    @Test
    void emailVerifiedFalse_whenClaimAbsentOrFalse() {
        assertFalse(service().createUserForOnboarding(base().build()).isEmailVerified());
        assertFalse(
                service()
                        .createUserForOnboarding(base().claim("email_verified", false).build())
                        .isEmailVerified());
    }

    @Test
    void emailVerifiedTrue_whenClaimTrue() {
        assertTrue(
                service()
                        .createUserForOnboarding(base().claim("email_verified", true).build())
                        .isEmailVerified());
    }

    @Test
    void setsBareAccountDefaultsAndSubject_butNoRole() {
        Jwt jwt = base().claim("email", "jordan@example.com").build();
        UserEntity u = service().createUserForOnboarding(jwt);

        assertEquals("google-sub-1", u.getOidcSubject());
        assertEquals("jordan@example.com", u.getEmail());
        assertEquals(AccountStatus.ACTIVE, u.getAccountStatus());
        assertEquals(AgeBand.ADULT, u.getAgeBand());
        verify(users).save(u);
    }

    @Test
    void fallsBackToGivenNameOnly_whenFamilyMissing() {
        // given_name present, family_name absent → trimmed "First " == "First".
        UserEntity u =
                service().createUserForOnboarding(base().claim("given_name", "Jordan").build());
        assertEquals("Jordan", u.getDisplayName());
    }
}
