package com.CampusToursLive.ai.bankend.controller;

import com.CampusToursLive.ai.bankend.model.User;
import com.CampusToursLive.ai.bankend.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/campusLive")
public class UserController {

    @Autowired
    UserService userService;

    @GetMapping("/user/{email}")
    public ResponseEntity<?> getUser(@PathVariable String email) {
        return userService.getUserByEmail(email)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("User not found"));
    }
    
    @PostMapping("/user")
    public ResponseEntity<User> createOrUpdateUser(@RequestBody User user) {
        return ResponseEntity.ok(userService.saveUser(user));
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginRequest) {
        boolean ok = userService.login(loginRequest.getEmail(), loginRequest.getPassword());

        if (!ok) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Invalid email or password");
        }

        return ResponseEntity.ok("Login successful");
    }
}
