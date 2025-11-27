package com.CampusToursLive.ai.bankend.repository;

import com.CampusToursLive.ai.bankend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

}
