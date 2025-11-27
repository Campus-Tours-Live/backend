package com.CampusToursLive.ai.bankend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {

    private String name;
    private int age;

    @Id
    private String email;  // Primary key
    private String password;
}
