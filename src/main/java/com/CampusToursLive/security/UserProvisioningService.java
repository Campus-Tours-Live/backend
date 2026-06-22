package com.CampusToursLive.security;

import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.AgeBand;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Just-in-time user provisioning. On first authenticated request from a new subject, create a BARE
 * domain user (one account ↔ one subject) — NO role and NO profile. The role is acquired when that
 * role's onboarding completes (writes user_roles + creates the profile, see RoleGrantService /
 * *Service).
 *
 * <p>Provisioning grants no role: a bare account can later become a participant, a guide, or both,
 * depending on which onboarding flows the user completes. The display name is read best-effort from
 * the id_token; onboarding fills in the rest.
 */
@Service
public class UserProvisioningService {

    private final UserRepository users;

    public UserProvisioningService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public UserEntity provisionFromJwt(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        Boolean emailVerified = jwt.getClaim("email_verified");

        String displayName =
                name != null
                        ? name
                        : ((firstName != null || lastName != null)
                                ? ((firstName == null ? "" : firstName)
                                                + " "
                                                + (lastName == null ? "" : lastName))
                                        .trim()
                                : (email != null ? email : "New user"));

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setOidcSubject(jwt.getSubject());
        user.setEmail(email);
        user.setEmailVerified(Boolean.TRUE.equals(emailVerified));
        // No role and no last_active_role yet — acquired at onboarding.
        user.setAccountStatus(AccountStatus.ACTIVE); // Google already verified the email
        user.setAgeBand(AgeBand.ADULT);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setDisplayName(displayName);
        user.setPreferredLanguage("en-US");
        user.setTimezone("America/Los_Angeles");
        users.save(user);

        return user;
    }
}
