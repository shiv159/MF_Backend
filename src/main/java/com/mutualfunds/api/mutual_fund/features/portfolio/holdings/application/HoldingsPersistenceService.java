package com.mutualfunds.api.mutual_fund.features.portfolio.holdings.application;

import com.mutualfunds.api.mutual_fund.features.funds.api.FundUpsertService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.persistence.UserHoldingRepository;
import com.mutualfunds.api.mutual_fund.features.users.api.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for persisting enriched holdings data to the database
 * Handles Fund creation/retrieval and UserHolding creation/updates
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HoldingsPersistenceService {

    private final UserHoldingRepository userHoldingRepository;
    private final FundUpsertService fundUpsertService;
    private final UserAccountService userAccountService;

    /**
     * Persist enriched holdings data to the database
     * For each enriched record:
     * 1. Find or create the Fund based on ISIN with enriched fund data (AMC,
     * category, NAV, etc.)
     * 2. Create or update UserHolding linking user to fund with holdings data
     * (units, value, etc.)
     *
     * @param enrichedData List of enriched holding records from ETL service
     * @param userId       User ID to associate holdings with
     * @return Number of holdings successfully persisted
     */
    @Transactional
    public Integer persistEnrichedHoldings(List<Map<String, Object>> enrichedData, UUID userId) {
        log.info("Starting to persist {} enriched holdings", enrichedData.size());

        User user = userAccountService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        int successCount = 0;

        for (Map<String, Object> holding : enrichedData) {
            try {
                // Extract Fund Master Data from enriched record (ETL response)
                String isin = extractString(holding, "isin");
                String fundName = extractString(holding, "fundName");
                Double currentNav = extractDouble(holding, "currentNav");

                // Extract User Holdings Data
                Double units = extractDouble(holding, "units");
                Double nav = extractDouble(holding, "nav");
                Double value = extractDouble(holding, "value");
                Date purchaseDate = extractDate(holding, "purchaseDate");

                // Validate required fields
                if (isin == null || fundName == null) {
                    log.warn("Skipping holding with invalid data. ISIN: {}, FundName: {}", isin, fundName);
                    continue;
                }

                // Find or create Fund with enriched data
                FundUpsertService.FundUpsertResult upsertResult = fundUpsertService.upsertFromEnriched(holding, false);
                Fund fund = upsertResult.fund();

                // Find or create UserHolding
                Optional<UserHolding> existingHolding = userHoldingRepository.findByUser_UserIdAndFund_FundId(userId,
                        fund.getFundId());

                UserHolding userHolding = existingHolding.orElseGet(UserHolding::new);
                userHolding.setUser(user);
                userHolding.setFund(fund);
                userHolding.setUnitsHeld(units);
                userHolding.setCurrentNav(currentNav);
                userHolding.setInvestmentAmount(value); // Mapped from 'value' in ETL response
                userHolding.setCurrentValue(value); // Calculated as units * currentNav
                userHolding.setPurchaseDate(purchaseDate);

                userHoldingRepository.save(userHolding);
                successCount++;
                log.debug("Persisted holding for fund ISIN: {}, units: {}, value: {}", isin, units, value);

            } catch (Exception e) {
                log.error("Failed to persist one enriched holding record", e);
            }
        }

        log.info("Successfully persisted {} out of {} enriched holdings", successCount, enrichedData.size());
        return successCount;
    }

    // Helper methods for type-safe extraction
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof Double)
            return (Double) value;
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse double value for key '{}': {}", key, value);
            return null;
        }
    }

    private Date extractDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof Date)
            return (Date) value;
        if (value instanceof java.util.Date)
            return new Date(((java.util.Date) value).getTime());
        if (value instanceof String) {
            try {
                return Date.valueOf((String) value);
            } catch (IllegalArgumentException e) {
                log.warn("Failed to parse date value for key '{}': {}", key, value);
                return null;
            }
        }
        return null;
    }

}
