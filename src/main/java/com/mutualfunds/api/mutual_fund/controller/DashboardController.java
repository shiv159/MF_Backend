package com.mutualfunds.api.mutual_fund.controller;

import com.mutualfunds.api.mutual_fund.dto.response.DashboardResponse;
import com.mutualfunds.api.mutual_fund.service.contract.IDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * Get complete dashboard data for authenticated user
     * User ID is extracted from the authenticated principal
     * 
     * @return DashboardResponse with user data
     */
    @GetMapping("/me")
    public ResponseEntity<DashboardResponse> getDashboard() {
        log.info("Dashboard /me request received");

        // Get authentication from SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("User not authenticated");
            return ResponseEntity.status(401).build();
        }

        // Extract user ID from our custom UserPrincipal
        com.mutualfunds.api.mutual_fund.security.UserPrincipal principal = (com.mutualfunds.api.mutual_fund.security.UserPrincipal) authentication
                .getPrincipal();

        UUID userId = principal.getUserId();
        log.info("Fetching dashboard data");

        // Get dashboard data
        DashboardResponse dashboard = dashboardService.getDashboardData(userId);

        log.info("Dashboard successfully retrieved");
        return ResponseEntity.ok(dashboard);
    }
}
