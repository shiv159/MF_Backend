package com.mutualfunds.api.mutual_fund.controller;

import com.mutualfunds.api.mutual_fund.dto.request.LoginRequest;
import com.mutualfunds.api.mutual_fund.dto.request.RegisterRequest;
import com.mutualfunds.api.mutual_fund.dto.response.AuthResponse;
import com.mutualfunds.api.mutual_fund.service.contract.IAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final IAuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("User registration attempt for email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        log.info("User registration successful for email: {}", request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("User login attempt for email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        log.info("User login successful for email: {}", request.getEmail());
        return ResponseEntity.ok(response);
    }

}