package com.mutualfunds.api.mutual_fund.controller;

import com.mutualfunds.api.mutual_fund.dto.response.DashboardResponse;
import com.mutualfunds.api.mutual_fund.security.JWTUtil;
import com.mutualfunds.api.mutual_fund.service.contract.IDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dashboard controller
 * Handles user dashboard data retrieval
 * All endpoints require JWT authentication
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final IDashboardService dashboardService;
    private final JWTUtil jwtUtil;

    /**
     * Get complete dashboard data for authenticated user
     * Extracts user ID from JWT token in Authorization header
     * 
     * @param authHeader Authorization header containing "Bearer {token}"
     * @return DashboardResponse with user profile, portfolio summary, holdings, uploads, and AI insights
     */
    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(@RequestHeader("Authorization") String authHeader) {
        try {
            log.info("Dashboard request received");

            // Extract JWT token from Authorization header
            String token = extractTokenFromHeader(authHeader);
            
            // Extract user ID from token
            UUID userId = jwtUtil.extractUserId(token);
            log.info("Fetching dashboard for user: {}", userId);

            // Get dashboard data
            DashboardResponse dashboard = dashboardService.getDashboardData(userId);
            
            log.info("Dashboard successfully retrieved for user: {}", userId);
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("Error retrieving dashboard data", e);
            throw e;
        }
    }

    /**
     * Alternative endpoint using Spring Security context
     * User ID is extracted from the authenticated principal
     * 
     * @return DashboardResponse with user data
     */
    @GetMapping("/me")
    public ResponseEntity<DashboardResponse> getDashboardFromContext() {
        try {
            log.info("Dashboard /me request received");

            // Get authentication from SecurityContext
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            // Extract user ID from JWT in the authentication details
            String token = (String) authentication.getCredentials();
            UUID userId = jwtUtil.extractUserId(token);
            log.info("Fetching dashboard for user: {}", userId);

            // Get dashboard data
            DashboardResponse dashboard = dashboardService.getDashboardData(userId);
            
            log.info("Dashboard successfully retrieved for user: {}", userId);
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("Error retrieving dashboard data", e);
            throw e;
        }
    }

    /**
     * Extract JWT token from Authorization header
     * Expected format: "Bearer {token}"
     * 
     * @param authHeader Authorization header value
     * @return JWT token string
     */
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.error("Invalid Authorization header format");
            throw new IllegalArgumentException("Invalid Authorization header format. Expected: Bearer {token}");
        }
        return authHeader.substring(7);
    }
}
