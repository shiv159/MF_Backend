package com.mutualfunds.api.mutual_fund.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.entity.UserHolding;
import com.mutualfunds.api.mutual_fund.repository.FundRepository;
import com.mutualfunds.api.mutual_fund.repository.UserHoldingRepository;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
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
    private final FundRepository fundRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        int successCount = 0;

        for (Map<String, Object> holding : enrichedData) {
            try {
                // Extract Fund Master Data from enriched record (ETL response)
                String isin = extractString(holding, "isin");
                String fundName = extractString(holding, "fundName");
                String amcName = extractString(holding, "amc");
                String fundCategory = extractString(holding, "category");
                Double expenseRatio = extractDouble(holding, "expenseRatio");
                Double currentNav = extractDouble(holding, "currentNav");
                LocalDate navAsOf = extractLocalDate(holding, "navAsOf");
                JsonNode sectorAllocation = extractJsonNode(holding, "sectorAllocation");
                JsonNode topHoldings = extractJsonNode(holding, "topHoldings");
                JsonNode fundMetadata = extractJsonNode(holding, "fundMetadata");

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
                Fund fund = fundRepository.findByIsin(isin)
                        .map(existingFund -> {
                            // Update existing fund with enriched data
                            if (amcName != null)
                                existingFund.setAmcName(amcName);
                            if (fundCategory != null)
                                existingFund.setFundCategory(fundCategory);
                            if (expenseRatio != null)
                                existingFund.setExpenseRatio(expenseRatio);
                            if (currentNav != null)
                                existingFund.setCurrentNav(currentNav);
                            if (navAsOf != null)
                                if (navAsOf != null)
                                    existingFund.setNavAsOf(java.sql.Date.valueOf(navAsOf));
                            if (sectorAllocation != null)
                                existingFund.setSectorAllocationJson(sectorAllocation);
                            if (topHoldings != null)
                                existingFund.setTopHoldingsJson(topHoldings);
                            if (fundMetadata != null)
                                existingFund.setFundMetadataJson(fundMetadata);
                            log.debug("Updating existing fund with ISIN: {} with enriched data", isin);
                            return fundRepository.save(existingFund);
                        })
                        .orElseGet(() -> {
                            // Create new fund with all enriched data
                            Fund newFund = Fund.builder()
                                    .isin(isin)
                                    .fundName(fundName)
                                    .amcName(amcName)
                                    .fundCategory(fundCategory)
                                    .expenseRatio(expenseRatio)
                                    .currentNav(currentNav)
                                    .navAsOf(navAsOf != null ? java.sql.Date.valueOf(navAsOf) : null)
                                    .sectorAllocationJson(sectorAllocation)
                                    .topHoldingsJson(topHoldings)
                                    .fundMetadataJson(fundMetadata)
                                    .directPlan(true)
                                    .build();
                            log.debug("Creating new fund with ISIN: {}, name: {}, AMC: {}, category: {}",
                                    isin, fundName, amcName, fundCategory);
                            return fundRepository.save(newFund);
                        });

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

    private LocalDate extractLocalDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof LocalDate)
            return (LocalDate) value;
        if (value instanceof java.util.Date)
            return ((java.util.Date) value).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        if (value instanceof String) {
            String s = (String) value;
            // Try ISO (yyyy-MM-dd)
            try {
                return LocalDate.parse(s);
            } catch (Exception ignored) {
            }
            // Try common day-first format (dd-MM-yyyy) as used by ETL
            try {
                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
                return LocalDate.parse(s, dtf);
            } catch (Exception ignored) {
            }
            // Try ISO date-time formats
            try {
                return java.time.OffsetDateTime.parse(s).toLocalDate();
            } catch (Exception ignored) {
            }
            try {
                return java.time.LocalDateTime.parse(s).toLocalDate();
            } catch (Exception ignored) {
            }

            log.warn("Failed to parse LocalDate value for key '{}': {}", key, value);
            return null;
        }
        return null;
    }

    private JsonNode extractJsonNode(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof JsonNode)
            return (JsonNode) value;
        try {
            return objectMapper.valueToTree(value);
        } catch (Exception e) {
            log.warn("Failed to convert value to JsonNode for key '{}': {}", key, value, e);
            return null;
        }
    }
}
