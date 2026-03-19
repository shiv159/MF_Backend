package com.mutualfunds.api.mutual_fund.features.portfolio.manual.api;

import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionRequest;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionResponse;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.api.IManualSelectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual portfolio selection controller
 * Replaces the authenticated user's holdings with selections (fundId from DB or fundName via ETL)
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Slf4j
public class ManualSelectionController {

    private final IManualSelectionService manualSelectionService;

    @PostMapping("/manual-selection")
    public ResponseEntity<ManualSelectionResponse> replaceHoldings(@Valid @RequestBody ManualSelectionRequest request) {
        log.info("Manual selection replaceHoldings request received with {} selections",
                request.getSelections() != null ? request.getSelections().size() : 0);
        return ResponseEntity.ok(manualSelectionService.replaceHoldingsWithManualSelection(request));
    }
}
