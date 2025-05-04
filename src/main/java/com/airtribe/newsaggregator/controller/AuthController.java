package com.airtribe.newsaggregator.controller;

import com.airtribe.newsaggregator.dto.AuthResponse;
import com.airtribe.newsaggregator.dto.LoginDto;
import com.airtribe.newsaggregator.dto.UserRegistrationDto;
import com.airtribe.newsaggregator.entity.User;
import com.airtribe.newsaggregator.security.JwtTokenProvider;
import com.airtribe.newsaggregator.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerUser(@Valid @RequestBody UserRegistrationDto registrationDto) {
        log.info("Attempting to register user: {}", registrationDto.getUsername());
        try {
            User user = userService.registerUser(registrationDto);
            Map<String, String> userData = new HashMap<>();
            userData.put("username", user.getUsername());
            userData.put("email", user.getEmail());
            log.info("Successfully registered user: {}", user.getUsername());
            return ResponseEntity.ok(AuthResponse.success("User registered successfully", userData));
        } catch (RuntimeException e) {
            log.error("Failed to register user: {}. Reason: {}", registrationDto.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(AuthResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginDto loginDto) {
        log.info("Attempting to authenticate user: {}", loginDto.getUsername());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginDto.getUsername(),
                            loginDto.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = tokenProvider.generateToken(authentication);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("token", jwt);
            responseData.put("type", "Bearer");
            responseData.put("username", loginDto.getUsername());
            
            log.info("Successfully authenticated user: {}", loginDto.getUsername());
            return ResponseEntity.ok(AuthResponse.success("Login successful", responseData));
        } catch (Exception e) {
            log.error("Failed to authenticate user: {}. Reason: {}", loginDto.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(AuthResponse.error("Invalid username or password"));
        }
    }
}
