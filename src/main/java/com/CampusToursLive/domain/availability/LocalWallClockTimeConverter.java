package com.CampusToursLive.domain.availability;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Persists wall-clock times as {@code HH:mm:ss} strings so {@code hibernate.jdbc.time_zone=UTC}
 * does not shift {@link LocalTime} values on write (e.g. 09:00 PST → 17:00).
 */
@Converter
public class LocalWallClockTimeConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_TIME;

    @Override
    public String convertToDatabaseColumn(LocalTime attribute) {
        return attribute == null ? null : attribute.truncatedTo(ChronoUnit.SECONDS).format(FORMAT);
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
        if (normalized.length() == 5) {
            normalized = normalized + ":00";
        }
        return LocalTime.parse(normalized, FORMAT);
    }
}
