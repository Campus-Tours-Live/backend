package com.CampusToursLive.domain.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps {@code guide_booking_settings} (V1__schema.sql). */
@Getter
@Setter
@Entity
@Table(name = "guide_booking_settings")
public class GuideBookingSettingsEntity {

    @Id
    @Column(name = "guide_id")
    private UUID guideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_mode", columnDefinition = "acceptance_mode", nullable = false)
    private AcceptanceMode acceptanceMode = AcceptanceMode.MANUAL;

    @Column(name = "response_deadline_min", nullable = false)
    private int responseDeadlineMin = 90;

    @Column(name = "min_notice_min", nullable = false)
    private int minNoticeMin = 1440;

    @Column(name = "max_advance_days", nullable = false)
    private int maxAdvanceDays = 30;

    @Column(name = "buffer_before_min", nullable = false)
    private int bufferBeforeMin = 0;

    @Column(name = "buffer_after_min", nullable = false)
    private int bufferAfterMin = 15;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "durations_offered", columnDefinition = "jsonb", nullable = false)
    private String durationsOffered = "[30,45,60,90]";

    @Column(name = "timezone", nullable = false)
    private String timezone = "America/Los_Angeles";

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
