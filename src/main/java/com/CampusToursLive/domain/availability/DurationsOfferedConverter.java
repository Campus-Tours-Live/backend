package com.CampusToursLive.domain.availability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * JPA converter for {@code guide_booking_settings.durations_offered} (a {@code jsonb} column): a
 * plain JSON array of tour-length minutes, e.g. {@code [30,45,60,90]}, mapped to {@code
 * List<Integer>} in Java. The datasource sets {@code stringtype=unspecified}
 * (application.properties), so a JSON-text bind value is accepted into the {@code jsonb} column
 * without an explicit cast — no {@code @JdbcTypeCode} needed.
 *
 * <p>Not {@code autoApply} — there is only one {@code List<Integer>} field on the entity and it
 * opts in explicitly via {@code @Convert(converter = DurationsOfferedConverter.class)}, so this
 * converter can never accidentally attach itself to an unrelated field.
 */
@Converter(autoApply = false)
public class DurationsOfferedConverter implements AttributeConverter<List<Integer>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Integer>> LIST_OF_INT = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize durationsOffered", ex);
        }
    }

    @Override
    public List<Integer> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, LIST_OF_INT);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse durationsOffered: " + dbData, ex);
        }
    }
}
