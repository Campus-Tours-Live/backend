package com.CampusToursLive.domain.availability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class LocalWallClockTimeConverterTest {

    private final LocalWallClockTimeConverter converter = new LocalWallClockTimeConverter();

    @Test
    void convertToDatabaseColumn_formatsTime() {
        assertEquals("09:00:00", converter.convertToDatabaseColumn(LocalTime.of(9, 0)));
    }

    @Test
    void convertToDatabaseColumn_truncatesNanoseconds() {
        assertEquals(
                "09:00:00", converter.convertToDatabaseColumn(LocalTime.of(9, 0, 0, 500_000_000)));
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToEntityAttribute_parsesFullTime() {
        assertEquals(LocalTime.of(21, 45), converter.convertToEntityAttribute("21:45:00"));
    }

    @Test
    void convertToEntityAttribute_parsesShortTime() {
        assertEquals(LocalTime.of(9, 0), converter.convertToEntityAttribute("09:00"));
    }

    @Test
    void convertToEntityAttribute_stripsFractionalSeconds() {
        assertEquals(LocalTime.of(9, 0), converter.convertToEntityAttribute("09:00:00.123"));
    }

    @Test
    void convertToEntityAttribute_nullAndBlank() {
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute("  "));
    }
}
