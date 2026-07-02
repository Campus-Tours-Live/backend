package com.CampusToursLive.domain.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps {@code availability_exceptions} (V1__schema.sql). */
@Getter
@Setter
@Entity
@Table(name = "availability_exceptions")
public class AvailabilityExceptionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", columnDefinition = "availability_exception_type", nullable = false)
    private AvailabilityExceptionType type;

    @Convert(converter = LocalWallClockTimeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "start_local", length = 8)
    private LocalTime startLocal;

    @Convert(converter = LocalWallClockTimeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "end_local", length = 8)
    private LocalTime endLocal;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
