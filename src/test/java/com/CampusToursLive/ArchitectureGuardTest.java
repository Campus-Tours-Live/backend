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
import org.junit.jupiter.api.io.TempDir;

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
 *       exactly the multi-snapshot bug the account-resolution redesign closed). The allowlist is a
 *       single entry — {@code OnboardingAccountRepository} (the write-side, lifecycle-inclusive
 *       counterpart onboarding uses; see {@link
 *       com.CampusToursLive.domain.user.OnboardingAccountRepository}'s class javadoc). {@code
 *       CurrentUser} and {@code AccountResolver} were previously listed — {@code CurrentUser}
 *       because its OAuth-callback JIT provisioning path once called the raw lookup, {@code
 *       AccountResolver} as the identity-resolution layer's speculative home for it — but CTL-97
 *       Task 11 removed the JIT path and neither class calls {@code findByOidcSubject} anymore
 *       (they resolve through the richer account-projection query), so both grants were dropped as
 *       of CTL-97 Core-B rather than left as vestigial permits: should a future read path in either
 *       class genuinely need the raw lookup, re-adding the grant is a deliberate, reviewable change
 *       — which is exactly what this guard is for. {@code OnboardingAccountRepository} is
 *       allowlisted BY TYPE even though its own method is named {@code findAnyByOidcSubject} —
 *       which would never trip {@link #FIND_BY_OIDC_SUBJECT} in the first place — precisely so the
 *       exception is an explicit, reviewable grant rather than an accident of naming that a future
 *       rename inside that file could silently defeat. {@link
 *       #noClassOutsideAllowlistCallsFindByOidcSubject_allowlistIsNotVacuous} proves this entry
 *       doesn't gut the rule: a synthetic {@code OnboardingAccountRepository.java} calling the raw
 *       method is permitted, but a synthetic file under any other name with the exact same
 *       offending call is still caught.
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

    /** See the class javadoc for why this file is exempt. */
    private static final Set<String> FIND_BY_OIDC_SUBJECT_ALLOWLIST =
            Set.of("OnboardingAccountRepository.java");

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

        List<String> violations =
                findByOidcSubjectViolations(MAIN_SRC, FIND_BY_OIDC_SUBJECT_ALLOWLIST);

        assertThat(violations)
                .as(
                        "only OnboardingAccountRepository may call the raw"
                                + " UserRepository.findByOidcSubject(...) — every other caller must go"
                                + " through the single-snapshot AccountResolver/CurrentUser gate"
                                + " instead of re-resolving the current user ad hoc. Core-B's"
                                + " lifecycle-inclusive onboarding lookup belongs behind"
                                + " OnboardingAccountRepository.findAnyByOidcSubject, not a stray"
                                + " findByOidcSubject call elsewhere")
                .isEmpty();
    }

    /**
     * Proves the {@code OnboardingAccountRepository.java} allowlist entry is a targeted, by-type
     * grant rather than one that quietly disables the rule everywhere. Two synthetic files sharing
     * the SAME offending {@code .findByOidcSubject(} call are scanned with the real production
     * allowlist and matcher ({@link #findByOidcSubjectViolations}): one named {@code
     * OnboardingAccountRepository.java} (must be permitted — the whole point of the allowlist
     * entry) and one named {@code SomeOtherRepository.java} (must still be caught — the whole point
     * of the rule). If a future change replaced the by-type allowlist with something broader (e.g.
     * matching any file whose name contains "Onboarding", or disabling the rule outright), this
     * test would catch it because the stray-caller file must still fail.
     */
    @Test
    void noClassOutsideAllowlistCallsFindByOidcSubject_allowlistIsNotVacuous(@TempDir Path tempSrc)
            throws IOException {
        String offendingLine = "userRepository.findByOidcSubject(subject);";
        writeJavaFile(tempSrc, "OnboardingAccountRepository.java", offendingLine);
        writeJavaFile(tempSrc, "SomeOtherRepository.java", offendingLine);

        List<String> violations =
                findByOidcSubjectViolations(tempSrc, FIND_BY_OIDC_SUBJECT_ALLOWLIST);

        assertThat(violations)
                .as(
                        "the allowlisted OnboardingAccountRepository.java must NOT be reported, but"
                                + " a same-content file under any other name must still be caught")
                .hasSize(1)
                .allMatch(violation -> violation.contains("SomeOtherRepository.java"));
    }

    private static void writeJavaFile(Path dir, String fileName, String bodyLine)
            throws IOException {
        Files.writeString(
                dir.resolve(fileName),
                "package example;\n\nclass "
                        + fileName.substring(0, fileName.length() - ".java".length())
                        + " {\n    void probe() {\n        "
                        + bodyLine
                        + "\n    }\n}\n");
    }

    /**
     * Core scan shared by the real rule and its vacuity proof: every {@code .java} file under
     * {@code root} not named in {@code allowlist}, checked for {@link #FIND_BY_OIDC_SUBJECT}.
     */
    private static List<String> findByOidcSubjectViolations(Path root, Set<String> allowlist)
            throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(ArchitectureGuardTest::isJavaFile)
                    .filter(file -> !allowlist.contains(file.getFileName().toString()))
                    .forEach(file -> collectMatches(file, FIND_BY_OIDC_SUBJECT, violations));
        }
        return violations;
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
