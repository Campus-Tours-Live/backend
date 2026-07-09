package com.CampusToursLive.domain.availability;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Persists wall-clock times as {@code HH:mm:ss} strings so {@code hibernate.jdbc.time_zone=UTC}
 * does not shift {@link LocalTime} values on write (e.g. 09:00 PST → 17:00).
 *
 * <p>Sub-second policy: nanoseconds are truncated to whole seconds on write; fractional seconds in
 * legacy DB values are stripped on read (everything after {@code .} is discarded). We do not build
 * a custom native {@code TIME} Hibernate type — VARCHAR + this converter is the project convention
 * for availability wall-clock fields.
 */
@Converter
public class LocalWallClockTimeConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter WALL_CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public String convertToDatabaseColumn(LocalTime attribute) {
        return attribute == null
                ? null
                : attribute.truncatedTo(ChronoUnit.SECONDS).format(WALL_CLOCK_FORMAT);
    }

    @Override
    public LocalTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String normalized = dbData.trim();
        int dot = normalized.indexOf('.');
        if (dot > 0) {
            normalized = normalized.substring(0, dot);
        }
        try {
            if (normalized.length() == 8) {
                return LocalTime.parse(normalized, WALL_CLOCK_FORMAT);
            }
            if (normalized.length() == 5) {
                return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm"));
            }
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid wall-clock time: " + dbData, ex);
        }
        throw new IllegalArgumentException("Invalid wall-clock time: " + dbData);
    }
}
