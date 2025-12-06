package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.dto.request.LoginRequest;
import com.mutualfunds.api.mutual_fund.dto.request.RegisterRequest;
import com.mutualfunds.api.mutual_fund.dto.response.AuthResponse;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.UserType;
import com.mutualfunds.api.mutual_fund.exception.BadRequestException;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import com.mutualfunds.api.mutual_fund.security.JWTUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    

    public AuthResponse register(RegisterRequest request) {
        

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        // Create user
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .userType(UserType.new_investor) // Default to new investor
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        // Generate token
        String token = jwtUtil.generateToken(savedUser.getEmail(), savedUser.getUserId());

        return AuthResponse.builder()
                .status("success")
                .accessToken(token)
                .userId(savedUser.getUserId())
                .email(savedUser.getEmail())
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Get user details
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Generate token
        String token = jwtUtil.generateToken(user.getEmail(), user.getUserId());

        return AuthResponse.builder()
                .status("success")
                .accessToken(token)
                .userId(user.getUserId())
                .email(user.getEmail())
                .createdAt(LocalDateTime.now())
                .build();
    }

    
}