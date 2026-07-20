package com.CampusToursLive.domain.university;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CampusImageUrlsTest {
    private final CampusImageUrls urls =
            new CampusImageUrls("https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/");

    @Test
    void encodesSpacesAsPercent20() {
        assertThat(urls.forName("University of California-Berkeley"))
                .isEqualTo(
                        "https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20California-Berkeley.png");
    }

    @Test
    void encodesAmpersandAsPercent26() {
        assertThat(urls.forName("Texas A&M University-College Station"))
                .isEqualTo(
                        "https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Texas%20A%26M%20University-College%20Station.png");
    }

    /**
     * L5#6 — the seed generator (scripts/generate-seed.mjs) must derive the SAME key for the same
     * name, or a seeded row points at an object that does not exist and the image 404s.
     *
     * <p>These names are the shared golden set: the JS side asserts the identical expectations at
     * run time (see `ENCODING_GOLDEN` there). Apostrophes are the case that actually differs -- JS
     * `encodeURIComponent` leaves `'` alone while Java's URLEncoder emits `%27` -- so a school like
     * "Saint Mary's College" would have silently 404'd. Keep both lists in step.
     */
    @Test
    void encodesApostropheAsPercent27() {
        assertThat(urls.forName("Saint Mary's College"))
                .isEqualTo(
                        "https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Saint%20Mary%27s%20College.png");
    }

    @Test
    void encodesParenthesesAndTilde() {
        // The other characters encodeURIComponent leaves bare but URLEncoder escapes.
        assertThat(urls.forName("Foo (Bar)~Baz!"))
                .isEqualTo(
                        "https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Foo%20%28Bar%29%7EBaz%21.png");
    }

    @Test
    void normalisesMissingTrailingSlashOnBase() {
        CampusImageUrls u = new CampusImageUrls("https://x.example");
        assertThat(u.forName("Yale University"))
                .isEqualTo("https://x.example/Yale%20University.png");
    }

    @Test
    void returnsNullForBlankName() {
        assertThat(urls.forName("  ")).isNull();
        assertThat(urls.forName(null)).isNull();
    }

    @Test
    void blankBaseYieldsNull() {
        // A blank base must return null (not a scheme-less relative "Name.png") so the frontend
        // falls back instead of crashing next/image at render. See CampusImageUrls#forName.
        assertThat(new CampusImageUrls("").forName("Yale University")).isNull();
    }
}
