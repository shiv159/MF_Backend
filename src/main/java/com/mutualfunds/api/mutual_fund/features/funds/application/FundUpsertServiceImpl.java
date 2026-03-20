package com.mutualfunds.api.mutual_fund.features.funds.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundUpsertService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.funds.persistence.FundRepository;
import com.mutualfunds.api.mutual_fund.shared.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundUpsertServiceImpl implements FundUpsertService {

    private final FundRepository fundRepository;
    private final ObjectMapper objectMapper;

    @Override
    public FundUpsertResult upsertFromEnriched(Map<String, Object> holding, boolean requireNav) {
        String isin = extractString(holding, "isin");
        String fundName = firstNonBlank(
                extractString(holding, "fund_name"),
                extractString(holding, "fundName"));
        String amcName = extractString(holding, "amc");
        String fundCategory = extractString(holding, "category");
        Double expenseRatio = extractDouble(holding, "expenseRatio");
        Double currentNav = extractDouble(holding, "currentNav");
        LocalDate navAsOf = extractLocalDate(holding, "navAsOf");
        JsonNode sectorAllocation = extractJsonNode(holding, "sectorAllocation");
        JsonNode topHoldings = extractJsonNode(holding, "topHoldings");
        JsonNode fundMetadata = extractJsonNode(holding, "fundMetadata");

        if (isin == null || fundName == null) {
            throw new ServiceUnavailableException("ETL", "ETL response missing required fields (isin/fundName)");
        }
        if (requireNav && currentNav == null) {
            throw new ServiceUnavailableException("ETL",
                    "ETL enrichment for '" + fundName + "' is incomplete: currentNav is null. The fund will not be saved. Please retry.");
        }

        Optional<Fund> existing = fundRepository.findByIsin(isin);
        if (existing.isPresent()) {
            Fund existingFund = existing.get();
            if (amcName != null)
                existingFund.setAmcName(amcName);
            if (fundCategory != null)
                existingFund.setFundCategory(fundCategory);
            if (expenseRatio != null)
                existingFund.setExpenseRatio(expenseRatio);
            if (currentNav != null)
                existingFund.setCurrentNav(currentNav);
            if (navAsOf != null)
                existingFund.setNavAsOf(java.sql.Date.valueOf(navAsOf));
            if (sectorAllocation != null)
                existingFund.setSectorAllocationJson(sectorAllocation);
            if (topHoldings != null)
                existingFund.setTopHoldingsJson(topHoldings);
            if (fundMetadata != null)
                existingFund.setFundMetadataJson(fundMetadata);
            if (fundName != null && !fundName.equals(existingFund.getFundName())) {
                existingFund.setFundName(fundName);
            }
            return new FundUpsertResult(fundRepository.save(existingFund), false);
        }

        Fund created = fundRepository.save(Fund.builder()
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
                .build());

        log.debug("Created new fund with ISIN: {}", isin);
        return new FundUpsertResult(created, true);
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        String stringValue = value.toString().trim();
        return stringValue.isEmpty() ? null : stringValue;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
            return null;
        }
    }

    private LocalDate extractLocalDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof LocalDate)
            return (LocalDate) value;
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof String s) {
            try {
                return LocalDate.parse(s);
            } catch (Exception ignored) {
            }
            try {
                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
                return LocalDate.parse(s, dtf);
            } catch (Exception ignored) {
            }
            try {
                return java.time.OffsetDateTime.parse(s).toLocalDate();
            } catch (Exception ignored) {
            }
            try {
                return java.time.LocalDateTime.parse(s).toLocalDate();
            } catch (Exception ignored) {
            }
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
            return null;
        }
    }
}
