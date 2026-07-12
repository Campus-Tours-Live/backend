package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.availability.IntervalMath.Span;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Table-driven tests for the PURE {@link IntervalMath} interval algebra — no DB, no Spring context.
 * This is the shared minute-of-day primitive that CTL-54 v2.1's weekly-overlap validation (Task 2),
 * date-specific trim/replace (Task 3), and dry-run preview (Task 4) all build on, so every boundary
 * case (touching bounds, prefix/suffix/middle cuts, cover-all, end-of-day == 1440) is asserted here
 * first.
 */
class IntervalMathTest {

    // ---------------------------------------------------------------------
    // Span construction
    // ---------------------------------------------------------------------

    @Test
    void span_validRange_isConstructed() {
        Span span = new Span(540, 660);

        assertThat(span.startMin()).isEqualTo(540);
        assertThat(span.endMin()).isEqualTo(660);
    }

    @Test
    void span_endMinAtMidnight_isAllowed() {
        Span span = new Span(1320, 1440);

        assertThat(span.startMin()).isEqualTo(1320);
        assertThat(span.endMin()).isEqualTo(1440);
    }

    @Test
    void span_endMinBeyondMidnight_isRejected() {
        assertThatThrownBy(() -> new Span(1320, 1560)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void span_startEqualsEnd_isRejected() {
        assertThatThrownBy(() -> new Span(600, 600)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void span_negativeStart_isRejected() {
        assertThatThrownBy(() -> new Span(-1, 60)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void span_startAfterEnd_isRejected() {
        assertThatThrownBy(() -> new Span(660, 540)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------------
    // overlaps
    // ---------------------------------------------------------------------

    @Test
    void overlaps_trueForOverlappingRanges() {
        Span a = new Span(540, 660);
        Span b = new Span(570, 600);

        assertThat(IntervalMath.overlaps(a, b)).isTrue();
        assertThat(IntervalMath.overlaps(b, a)).isTrue();
    }

    @Test
    void overlaps_falseForTouchingBounds() {
        Span a = new Span(540, 660);
        Span b = new Span(660, 720);

        assertThat(IntervalMath.overlaps(a, b)).isFalse();
        assertThat(IntervalMath.overlaps(b, a)).isFalse();
    }

    @Test
    void overlaps_falseForDisjointRanges() {
        Span a = new Span(540, 600);
        Span b = new Span(600, 660);

        assertThat(IntervalMath.overlaps(a, b)).isFalse();
    }

    // ---------------------------------------------------------------------
    // subtract
    // ---------------------------------------------------------------------

    @Test
    void subtract_middleCut_returnsTwoOrderedRemnants() {
        Span minuend = new Span(540, 660);
        Span cut = new Span(570, 600);

        List<Span> result = IntervalMath.subtract(minuend, cut);

        assertThat(result).containsExactly(new Span(540, 570), new Span(600, 660));
    }

    @Test
    void subtract_prefixCut_returnsSingleSuffixRemnant() {
        Span minuend = new Span(540, 660);
        Span cut = new Span(540, 570);

        List<Span> result = IntervalMath.subtract(minuend, cut);

        assertThat(result).containsExactly(new Span(570, 660));
    }

    @Test
    void subtract_suffixCut_returnsSinglePrefixRemnant() {
        Span minuend = new Span(540, 660);
        Span cut = new Span(600, 660);

        List<Span> result = IntervalMath.subtract(minuend, cut);

        assertThat(result).containsExactly(new Span(540, 600));
    }

    @Test
    void subtract_exactCoverAll_returnsEmpty() {
        Span minuend = new Span(540, 660);
        Span cut = new Span(540, 660);

        List<Span> result = IntervalMath.subtract(minuend, cut);

        assertThat(result).isEmpty();
    }

    @Test
    void subtract_cutSupersetOfMinuend_returnsEmpty() {
        Span minuend = new Span(540, 660);
        Span cut = new Span(500, 700);

        List<Span> result = IntervalMath.subtract(minuend, cut);

        assertThat(result).isEmpty();
    }

    @Test
    void subtract_disjointCut_returnsMinuendUnchanged() {
        Span minuend = new Span(540, 660);
        Span cut = new Span(700, 800);

        List<Span> result = IntervalMath.subtract(minuend, cut);

        assertThat(result).containsExactly(minuend);
    }

    @Test
    void subtract_touchingCut_returnsMinuendUnchanged() {
        Span minuend = new Span(540, 660);
        Span cut = new Span(660, 720);

        List<Span> result = IntervalMath.subtract(minuend, cut);

        assertThat(result).containsExactly(minuend);
    }

    // ---------------------------------------------------------------------
    // spanOf
    // ---------------------------------------------------------------------

    @Test
    void spanOf_endsExactlyAtMidnight_isValid() {
        Span span = IntervalMath.spanOf(LocalTime.of(22, 0), 120);

        assertThat(span).isEqualTo(new Span(1320, 1440));
    }

    @Test
    void spanOf_typicalDaytimeWindow() {
        Span span = IntervalMath.spanOf(LocalTime.of(9, 0), 120);

        assertThat(span).isEqualTo(new Span(540, 660));
    }
}
