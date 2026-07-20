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
