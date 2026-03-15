package com.mutualfunds.api.mutual_fund.alert.service;

import com.mutualfunds.api.mutual_fund.alert.dto.AlertResponse;
import com.mutualfunds.api.mutual_fund.alert.model.AlertStatus;
import com.mutualfunds.api.mutual_fund.entity.PortfolioAlert;
import com.mutualfunds.api.mutual_fund.repository.PortfolioAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioAlertService {

    private final PortfolioAlertRepository portfolioAlertRepository;

    public List<AlertResponse> listAlerts(UUID userId) {
        return portfolioAlertRepository.findByUser_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public AlertResponse acknowledge(UUID userId, UUID alertId) {
        PortfolioAlert alert = portfolioAlertRepository.findByAlertIdAndUser_UserId(alertId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        return toResponse(portfolioAlertRepository.save(alert));
    }

    private AlertResponse toResponse(PortfolioAlert alert) {
        return AlertResponse.builder()
                .alertId(alert.getAlertId().toString())
                .type(alert.getType().name())
                .severity(alert.getSeverity().name())
                .title(alert.getTitle())
                .body(alert.getBody())
                .status(alert.getStatus().name())
                .payload(alert.getPayloadJson())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
