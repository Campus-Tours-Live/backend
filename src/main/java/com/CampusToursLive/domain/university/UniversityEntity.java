package com.CampusToursLive.domain.university;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Maps the {@code universities} catalog table (V1__schema.sql). Read-only here. */
@Getter
@Setter
@Entity
@Table(name = "universities")
public class UniversityEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "region")
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "university_status", nullable = false)
    private UniversityStatus status;
}
