package com.CampusToursLive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Grep-style architecture guard (CTL-97 Core-A Task 6) — a cheap alternative to pulling in ArchUnit
 * for two narrow rules that keep the account-resolution redesign from regressing:
 *
 * <ol>
 *   <li><b>No {@code web/*Controller} calls the removed {@code .require()} gate.</b> Every
 *       role-scoped endpoint must resolve identity through {@code requireProvisioned()} (or one of
 *       the typed helpers built on it — {@code requireRole}, {@code requireGuide}, {@code
 *       requireParticipant}, {@code requireNonProfileRole}), so a pending caller never collapses
 *       into a bare 401 (I10). {@link com.CampusToursLive.security.CurrentUser#require()} was
 *       removed in this same task specifically because it was the one path that still did that —
 *       this guard exists so it (or an equivalent shortcut) can't quietly come back.
 *   <li><b>No class outside the identity-resolution allowlist calls {@code
 *       UserRepository.findByOidcSubject(...)}.</b> That repository method is the raw, unclassified
 *       lookup by OIDC subject — every other caller must resolve the current user through the
 *       single-snapshot {@link com.CampusToursLive.security.AccountResolver} /{@link
 *       com.CampusToursLive.security.CurrentUser} gate instead of re-resolving it ad hoc (which is
 *       exactly the multi-snapshot bug the account-resolution redesign closed). The allowlist is
 *       {@code CurrentUser} (its {@code resolve(intent)} OAuth-callback path legitimately needs the
 *       raw, ACTIVE-only lookup) and {@code AccountResolver} (listed for the identity-resolution
 *       layer even though it currently reads the richer account-projection query instead).
 *       <b>Core-B note:</b> its onboarding flow needs a lifecycle-inclusive lookup that also sees
 *       pending accounts — that belongs behind a dedicated repository method (e.g. {@code
 *       OnboardingAccountRepository.findAnyByOidcSubject}), not by adding a new class to this
 *       allowlist or by reusing {@code findByOidcSubject} elsewhere. This keeps the guard exact
 *       without needing to be edited when Core-B lands.
 * </ol>
 *
 * <p>Deliberately does <b>not</b> forbid {@code findById}/{@code findByUserId} — managed-entity
 * loads (e.g. a PATCH handler re-loading its row by {@code account.userId()}) are allowed and
 * expected; only the raw by-subject lookup is gated.
 */
class ArchitectureGuardTest {

    private static final Path MAIN_SRC = Paths.get("src", "main", "java");
    private static final Path WEB_DIR =
            MAIN_SRC.resolve(Paths.get("com", "CampusToursLive", "web"));

    private static final Pattern REQUIRE_NO_ARGS = Pattern.compile("\\.require\\(\\)");
    private static final Pattern FIND_BY_OIDC_SUBJECT = Pattern.compile("\\.findByOidcSubject\\(");

    /** See the class javadoc for why each of these two files is exempt. */
    private static final Set<String> FIND_BY_OIDC_SUBJECT_ALLOWLIST =
            Set.of("CurrentUser.java", "AccountResolver.java");

    @Test
    void noControllerCallsTheRemovedRequireGate() throws IOException {
        assertThat(Files.isDirectory(WEB_DIR))
                .as("expected %s to exist — is this test running from the module root?", WEB_DIR)
                .isTrue();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(WEB_DIR)) {
            files.filter(ArchitectureGuardTest::isControllerFile)
                    .forEach(file -> collectMatches(file, REQUIRE_NO_ARGS, violations));
        }

        assertThat(violations)
                .as(
                        "web/*Controller must resolve identity via requireProvisioned() (or"
                                + " requireRole/requireGuide/requireParticipant/requireNonProfileRole"
                                + " built on it), never the removed .require() — that was the one path"
                                + " that collapsed a pending account into a bare 401 (I10)")
                .isEmpty();
    }

    @Test
    void noClassOutsideAllowlistCallsFindByOidcSubject() throws IOException {
        assertThat(Files.isDirectory(MAIN_SRC))
                .as("expected %s to exist — is this test running from the module root?", MAIN_SRC)
                .isTrue();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SRC)) {
            files.filter(ArchitectureGuardTest::isJavaFile)
                    .filter(
                            file ->
                                    !FIND_BY_OIDC_SUBJECT_ALLOWLIST.contains(
                                            file.getFileName().toString()))
                    .forEach(file -> collectMatches(file, FIND_BY_OIDC_SUBJECT, violations));
        }

        assertThat(violations)
                .as(
                        "only CurrentUser/AccountResolver may call the raw"
                                + " UserRepository.findByOidcSubject(...) — every other caller must go"
                                + " through the single-snapshot AccountResolver/CurrentUser gate"
                                + " instead of re-resolving the current user ad hoc. Core-B's"
                                + " lifecycle-inclusive onboarding lookup belongs behind a dedicated"
                                + " repository method (e.g."
                                + " OnboardingAccountRepository.findAnyByOidcSubject), not here")
                .isEmpty();
    }

    private static boolean isControllerFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith("Controller.java");
    }

    private static boolean isJavaFile(Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().endsWith(".java");
    }

    /**
     * Appends {@code "path:line: content"} for every line in {@code file} matching {@code pattern}.
     */
    private static void collectMatches(Path file, Pattern pattern, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (pattern.matcher(line).find()) {
                violations.add(file + ":" + (i + 1) + ": " + line.strip());
            }
        }
    }
}
