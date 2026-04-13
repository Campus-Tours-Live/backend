package com.CampusToursLive.ai.bankend.controller;

import com.CampusToursLive.ai.bankend.dto.RequestInfo;
import com.CampusToursLive.ai.bankend.model.User;
import com.CampusToursLive.ai.bankend.service.RequestInfoService;
import com.CampusToursLive.ai.bankend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/campusLive")
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    RequestInfoService requestInfoService;

    @GetMapping("/user/{email}")
    public ResponseEntity<?> getUser(@PathVariable String email, HttpServletRequest request) {
        RequestInfo requestInfo = requestInfoService.extract(request);

        return userService.getUserByEmail(email)
                .<ResponseEntity<?>>map(user -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("user", user);
                    body.put("requestInfo", requestInfo);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("User not found"));
    }

    @PostMapping("/user")
    public ResponseEntity<?> createOrUpdateUser(@RequestBody User user, HttpServletRequest request) {
        User saved = userService.saveUser(user);
        RequestInfo requestInfo = requestInfoService.extract(request);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", saved);
        body.put("requestInfo", requestInfo);

        return ResponseEntity.ok(body);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginRequest, HttpServletRequest request) {
        boolean ok = userService.login(loginRequest.getEmail(), loginRequest.getPassword());
        RequestInfo requestInfo = requestInfoService.extract(request);

        if (!ok) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Invalid email or password");
            body.put("requestInfo", requestInfo);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Login successful");
        body.put("requestInfo", requestInfo);

        return ResponseEntity.ok(body);
    }
}