package com.mutualfunds.api.mutual_fund.alert.controller;

import com.mutualfunds.api.mutual_fund.alert.dto.AlertResponse;
import com.mutualfunds.api.mutual_fund.alert.service.PortfolioAlertService;
import com.mutualfunds.api.mutual_fund.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final PortfolioAlertService portfolioAlertService;

    @GetMapping
    public List<AlertResponse> listAlerts(Authentication authentication) {
        return portfolioAlertService.listAlerts(extractUserId(authentication));
    }

    @PostMapping("/{alertId}/acknowledge")
    public AlertResponse acknowledge(@PathVariable UUID alertId, Authentication authentication) {
        return portfolioAlertService.acknowledge(extractUserId(authentication), alertId);
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }
        return userPrincipal.getUserId();
    }
}
