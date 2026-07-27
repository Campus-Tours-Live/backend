package com.CampusToursLive.domain.guide;

import static org.assertj.core.api.Assertions.assertThat;

import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository integration test against a REAL PostgreSQL (Testcontainers) — {@code
 * guide_universities} (V1__schema.sql) requires valid {@code guide_profiles} and {@code
 * universities} FK parents, plus its {@code guide_verification_status} PG enum column.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GuideUniversityRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private GuideUniversityRepository guideUniversities;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private UniversityRepository universities;

    private UUID guideProfileId;
    private UUID universityId;

    @BeforeEach
    void seedGuideProfile() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();
        universityId = university.getId();

        UserEntity guideUser = new UserEntity();
        guideUser.setId(UUID.randomUUID());
        guideUser.setOidcSubject("it-" + UUID.randomUUID());
        guideUser.setEmail("it-" + UUID.randomUUID() + "@example.com");
        guideUser.setDisplayName("Jane Guide");
        guideUser.setAccountStatus(AccountStatus.ACTIVE);
        guideUser.setPreferredLanguage("en-US");
        guideUser.setTimezone("America/Los_Angeles");
        users.save(guideUser);

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(UUID.randomUUID());
        guide.setUserId(guideUser.getId());
        guide.setUniversityId(universityId);
        guide.setMajor("Computer Science");
        guide.setApplicationStatus(GuideApplicationStatus.VERIFIED);
        guides.save(guide);

        guideProfileId = guide.getId();
    }

    @Test
    void findByGuideProfileId_returnsSavedRow_withNotSubmittedDefault() {
        GuideUniversityEntity offering = new GuideUniversityEntity();
        offering.setId(UUID.randomUUID());
        offering.setGuideProfileId(guideProfileId);
        offering.setUniversityId(universityId);
        offering.setMajor("Computer Science");
        offering.setDegree("BS");
        offering.setClassYear("Junior");
        offering.setSchoolEmail("jane@school.edu");
        offering.setVerificationStatus(GuideVerificationStatus.NOT_SUBMITTED);
        guideUniversities.save(offering);

        List<GuideUniversityEntity> found = guideUniversities.findByGuideProfileId(guideProfileId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getVerificationStatus())
                .isEqualTo(GuideVerificationStatus.NOT_SUBMITTED);
        assertThat(found.get(0).getUniversityId()).isEqualTo(universityId);
        assertThat(found.get(0).getSchoolEmail()).isEqualTo("jane@school.edu");
    }
}
