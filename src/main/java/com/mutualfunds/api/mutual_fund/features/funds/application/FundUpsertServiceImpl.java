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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundUpsertServiceImpl implements FundUpsertService {

    private final FundRepository fundRepository;
    private final ObjectMapper objectMapper;

    @Override
    public FundUpsertResult upsertFromEnriched(Map<String, Object> holding, boolean requireCompleteData) {
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

        if (requireCompleteData) {
            List<String> missingOrInvalid = new ArrayList<>();
            if (currentNav == null) {
                missingOrInvalid.add("currentNav");
            }
            if (navAsOf == null) {
                missingOrInvalid.add("navAsOf");
            }
            if (!hasUsableJsonContent(sectorAllocation)) {
                missingOrInvalid.add("sectorAllocation");
            }
            if (!hasUsableJsonContent(topHoldings)) {
                missingOrInvalid.add("topHoldings");
            }
            if (!hasUsableJsonContent(fundMetadata)) {
                missingOrInvalid.add("fundMetadata");
            }
            missingOrInvalid.addAll(readQualityIssues(fundMetadata));

            if (!missingOrInvalid.isEmpty()) {
                throw new ServiceUnavailableException("ETL",
                        "ETL enrichment for '" + fundName + "' is incomplete: missing or invalid " +
                                String.join(", ", missingOrInvalid) + ". The fund will not be saved. Please retry.");
            }
        }

        Optional<Fund> existing = fundRepository.findByIsin(isin);
        if (existing.isPresent()) {
            Fund existingFund = existing.get();
            if (amcName != null) {
                existingFund.setAmcName(amcName);
            }
            if (fundCategory != null) {
                existingFund.setFundCategory(fundCategory);
            }
            if (expenseRatio != null) {
                existingFund.setExpenseRatio(expenseRatio);
            }
            if (currentNav != null) {
                existingFund.setCurrentNav(currentNav);
            }
            if (navAsOf != null) {
                existingFund.setNavAsOf(java.sql.Date.valueOf(navAsOf));
            }
            if (hasUsableJsonContent(sectorAllocation)) {
                existingFund.setSectorAllocationJson(sectorAllocation);
            }
            if (hasUsableJsonContent(topHoldings)) {
                existingFund.setTopHoldingsJson(topHoldings);
            }
            if (hasUsableJsonContent(fundMetadata) && readQualityIssues(fundMetadata).isEmpty()) {
                existingFund.setFundMetadataJson(fundMetadata);
            }
            if (!fundName.equals(existingFund.getFundName())) {
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
                .sectorAllocationJson(hasUsableJsonContent(sectorAllocation) ? sectorAllocation : null)
                .topHoldingsJson(hasUsableJsonContent(topHoldings) ? topHoldings : null)
                .fundMetadataJson(hasUsableJsonContent(fundMetadata) ? fundMetadata : null)
                .directPlan(true)
                .build());

        log.debug("Created new fund with ISIN: {}", isin);
        return new FundUpsertResult(created, true);
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
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
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate extractLocalDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.util.Date dateValue) {
            return dateValue.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof String stringValue) {
            try {
                return LocalDate.parse(stringValue);
            } catch (Exception ignored) {
            }
            try {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
                return LocalDate.parse(stringValue, formatter);
            } catch (Exception ignored) {
            }
            try {
                return java.time.OffsetDateTime.parse(stringValue).toLocalDate();
            } catch (Exception ignored) {
            }
            try {
                return java.time.LocalDateTime.parse(stringValue).toLocalDate();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private JsonNode extractJsonNode(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode) {
            return (JsonNode) value;
        }
        try {
            return objectMapper.valueToTree(value);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasUsableJsonContent(JsonNode node) {
        return node != null && !node.isNull() && !node.isEmpty();
    }

    private List<String> readQualityIssues(JsonNode fundMetadata) {
        if (!hasUsableJsonContent(fundMetadata)) {
            return Collections.emptyList();
        }

        JsonNode qualityNode = fundMetadata.path("data_quality");
        if (qualityNode.isMissingNode() || qualityNode.isNull() || qualityNode.isEmpty()) {
            qualityNode = fundMetadata.path("mstarpy_metadata").path("data_quality");
        }

        if (qualityNode.isMissingNode() || qualityNode.isNull() || qualityNode.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> issues = new ArrayList<>();
        appendQualityValues(qualityNode.path("missing_fields"), "fundMetadata.", issues);
        appendQualityValues(qualityNode.path("quality_flags"), "fundMetadata.", issues);
        return issues;
    }

    private void appendQualityValues(JsonNode values, String prefix, List<String> issues) {
        if (!values.isArray() || values.isEmpty()) {
            return;
        }

        for (JsonNode value : values) {
            if (value.isTextual() && !value.asText().isBlank()) {
                issues.add(prefix + value.asText());
            }
        }
    }
}
