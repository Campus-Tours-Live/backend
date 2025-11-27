package com.CampusToursLive.ai.bankend.service;

import com.CampusToursLive.ai.bankend.model.User;
import com.CampusToursLive.ai.bankend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findById(email);
    }
    
    public User saveUser(User user) {
        return userRepository.save(user);
    }
    
    public boolean login(String email, String password) {
        return userRepository.findById(email)
                .map(u -> u.getPassword().equals(password))
                .orElse(false);
    }
}
