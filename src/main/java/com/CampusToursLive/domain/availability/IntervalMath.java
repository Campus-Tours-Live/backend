package com.CampusToursLive.domain.availability;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, half-open {@code [start,end)} minute-of-day interval algebra — no Spring, no DB, no
 * dependency beyond {@code java.time}/{@code java.util}. This is the shared primitive that CTL-54
 * v2.1's weekly-rule overlap validation, date-specific trim/replace, and dry-run preview all build
 * on: one place owns "do these two time-of-day ranges overlap" and "what remains of a range after
 * another range cuts into it".
 *
 * <p>A single weekly rule or date-specific exception never crosses midnight (a cross-midnight range
 * is modeled as two adjacent-day rows upstream), so every {@link Span} here is bounded to a single
 * day: {@code 0 <= startMin < endMin <= 1440}. {@code endMin == 1440} is the valid "ends exactly at
 * 12:00 AM / end-of-day" case.
 */
public final class IntervalMath {

    private IntervalMath() {}

    /**
     * A half-open {@code [startMin, endMin)} range of minutes-of-day, {@code 0..1440}.
     *
     * <p>{@code endMin} may equal {@code 1440} (a range ending exactly at midnight is valid); a
     * single {@code Span} never crosses midnight, so {@code endMin} never exceeds {@code 1440}.
     */
    public record Span(int startMin, int endMin) {

        public Span {
            if (startMin < 0 || startMin >= endMin || endMin > 1440) {
                throw new IllegalArgumentException(
                        "Span must satisfy 0 <= startMin < endMin <= 1440, got startMin="
                                + startMin
                                + ", endMin="
                                + endMin);
            }
        }
    }

    /**
     * Whether two half-open spans overlap. Touching bounds (one span's {@code endMin} equals the
     * other's {@code startMin}) do NOT count as overlapping.
     */
    public static boolean overlaps(Span a, Span b) {
        return a.startMin() < b.endMin() && b.startMin() < a.endMin();
    }

    /**
     * {@code minuend} minus {@code cut} -&gt; the 0, 1, or 2 remnant spans of {@code minuend} that
     * remain outside of {@code cut}. This is the trim primitive behind date-specific newest-wins
     * override replacement (Task 3) and its dry-run mirror (Task 4).
     *
     * <ul>
     *   <li>No overlap -&gt; {@code minuend} unchanged (one span).
     *   <li>{@code cut} covers all of {@code minuend} -&gt; empty list.
     *   <li>{@code cut} clips the middle -&gt; two spans (prefix + suffix), in order.
     *   <li>{@code cut} clips a prefix or a suffix -&gt; one remnant span.
     * </ul>
     *
     * <p>Never emits a zero- or negative-width span.
     */
    public static List<Span> subtract(Span minuend, Span cut) {
        if (!overlaps(minuend, cut)) {
            return List.of(minuend);
        }

        List<Span> remnants = new ArrayList<>(2);
        if (cut.startMin() > minuend.startMin()) {
            remnants.add(new Span(minuend.startMin(), cut.startMin()));
        }
        if (cut.endMin() < minuend.endMin()) {
            remnants.add(new Span(cut.endMin(), minuend.endMin()));
        }
        return remnants;
    }

    /**
     * Converts a {@link LocalTime} start + a duration in minutes into a {@link Span}. Callers must
     * validate {@code start + windowMin <= 1440} BEFORE calling this (the same-day constraint);
     * {@link Span}'s own constructor is the backstop if they don't.
     */
    public static Span spanOf(LocalTime start, int windowMin) {
        int startMin = start.toSecondOfDay() / 60;
        return new Span(startMin, startMin + windowMin);
    }
}
