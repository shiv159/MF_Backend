package com.mutualfunds.api.mutual_fund.service.contract;

import com.mutualfunds.api.mutual_fund.dto.request.LoginRequest;
import com.mutualfunds.api.mutual_fund.dto.request.RegisterRequest;
import com.mutualfunds.api.mutual_fund.dto.response.AuthResponse;

/**
 * Contract for authentication service
 * Defines operations for user registration and login
 */
public interface IAuthService {
    
    /**
     * Register a new user
     * 
     * @param request RegisterRequest with user details
     * @return AuthResponse with JWT token
     */
    AuthResponse register(RegisterRequest request);
    
    /**
     * Authenticate and login a user
     * 
     * @param request LoginRequest with email and password
     * @return AuthResponse with JWT token
     */
    AuthResponse login(LoginRequest request);
}
