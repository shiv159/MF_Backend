package com.mutualfunds.api.mutual_fund.service.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.dto.EnrichmentResult;
import com.mutualfunds.api.mutual_fund.dto.manual.*;
import com.mutualfunds.api.mutual_fund.dto.request.ParsedHoldingEntry;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.entity.UserHolding;
import com.mutualfunds.api.mutual_fund.exception.BadRequestException;
import com.mutualfunds.api.mutual_fund.exception.ServiceUnavailableException;
import com.mutualfunds.api.mutual_fund.repository.FundRepository;
import com.mutualfunds.api.mutual_fund.repository.UserHoldingRepository;
import com.mutualfunds.api.mutual_fund.service.ETLEnrichmentService;
import com.mutualfunds.api.mutual_fund.service.contract.IOnboardingService;
import com.mutualfunds.api.mutual_fund.service.manual.contract.IManualSelectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualSelectionService implements IManualSelectionService {

    private static final String STATUS_RESOLVED_FROM_DB = "RESOLVED_FROM_DB";
    private static final String STATUS_CREATED_FROM_ETL = "CREATED_FROM_ETL";
    private static final String STATUS_ENRICHED_FROM_ETL = "ENRICHED_FROM_ETL";

    private final IOnboardingService onboardingService;
    private final FundRepository fundRepository;
    private final UserHoldingRepository userHoldingRepository;
    private final ETLEnrichmentService etlEnrichmentService;
    private final ObjectMapper objectMapper;
    private final com.mutualfunds.api.mutual_fund.service.risk.PortfolioAnalyzerService portfolioAnalyzerService;

    @Override
    @Transactional
    public ManualSelectionResponse replaceHoldingsWithManualSelection(ManualSelectionRequest request) {
        User currentUser = onboardingService.getCurrentUser();
        UUID userId = currentUser.getUserId();

        List<ManualSelectionItemRequest> selections = request.getSelections();
        validateWeights(selections);

        // 1) Resolve DB selections immediately
        List<ManualSelectionResult> results = new ArrayList<>();
        Map<Integer, Fund> resolvedFundsByIndex = new HashMap<>();
        Map<Integer, Integer> weightPctByIndex = new HashMap<>();

        List<Integer> etlIndexes = new ArrayList<>();
        List<ParsedHoldingEntry> etlRequests = new ArrayList<>();

        for (int i = 0; i < selections.size(); i++) {
            ManualSelectionItemRequest item = selections.get(i);
            weightPctByIndex.put(i, item.getWeightPct());

            if (item.getFundId() != null) {
                Fund fund = fundRepository.findById(item.getFundId())
                        .orElseThrow(() -> new BadRequestException("Fund not found for fundId: " + item.getFundId()));
                resolvedFundsByIndex.put(i, fund);
                results.add(ManualSelectionResult.builder()
                        .inputFundId(item.getFundId())
                        .inputFundName(null)
                        .status(STATUS_RESOLVED_FROM_DB)
                        .fundId(fund.getFundId())
                        .fundName(fund.getFundName())
                        .isin(fund.getIsin())
                        .message("Resolved from database")
                        .build());
            } else {
                etlIndexes.add(i);
                etlRequests.add(ParsedHoldingEntry.builder()
                        .fundName(item.getFundName().trim())
                        .units(1.0)
                        .build());
                results.add(ManualSelectionResult.builder()
                        .inputFundId(null)
                        .inputFundName(item.getFundName())
                        .status(STATUS_ENRICHED_FROM_ETL)
                        .message("Resolved via ETL")
                        .build());
            }
        }

        // 2) Enrich missing funds via ETL in a single batch call
        if (!etlRequests.isEmpty()) {
            EnrichmentResult enrichmentResult;
            try {
                enrichmentResult = etlEnrichmentService.enrichPortfolioData(etlRequests, userId, "manual");
            } catch (Exception e) {
                throw new ServiceUnavailableException("ETL", "Failed to enrich funds: " + e.getMessage());
            }

            List<Map<String, Object>> enrichedData = enrichmentResult.getEnrichedData();
            if (enrichedData == null || enrichedData.size() != etlRequests.size()) {
                throw new ServiceUnavailableException(
                        "ETL",
                        "ETL returned unexpected result count. Expected " + etlRequests.size() + ", got " +
                                (enrichedData == null ? 0 : enrichedData.size()));
            }

            for (int j = 0; j < enrichedData.size(); j++) {
                int selectionIndex = etlIndexes.get(j);
                Map<String, Object> enrichedMap = enrichedData.get(j);
                FundUpsertOutcome upsertOutcome = upsertFundFromEtl(enrichedMap);
                resolvedFundsByIndex.put(selectionIndex, upsertOutcome.fund());

                // fill in result fields
                ManualSelectionResult existing = results.get(selectionIndex);
                existing.setFundId(upsertOutcome.fund().getFundId());
                existing.setFundName(upsertOutcome.fund().getFundName());
                existing.setIsin(upsertOutcome.fund().getIsin());
                existing.setStatus(upsertOutcome.created() ? STATUS_CREATED_FROM_ETL : STATUS_ENRICHED_FROM_ETL);
            }
        }

        // 3) Validate no duplicates after resolution
        validateNoDuplicateFunds(resolvedFundsByIndex, selections.size());

        // 4) Replace holdings + allocations atomically
        userHoldingRepository.deleteByUser_UserId(userId);

        // Ensure deletes are executed before the following batch insert.
        // Without this, Hibernate may order INSERTs before DELETEs within the same
        // transaction,
        // causing (user_id, fund_id) unique constraint violations.
        userHoldingRepository.flush();

        List<UserHolding> newHoldings = new ArrayList<>(selections.size());

        for (int i = 0; i < selections.size(); i++) {
            Fund fund = resolvedFundsByIndex.get(i);
            Integer weightPct = weightPctByIndex.get(i);
            if (fund == null) {
                throw new BadRequestException("Unable to resolve fund for selection index: " + i);
            }

            UserHolding holding = UserHolding.builder()
                    .user(currentUser)
                    .fund(fund)
                    .weightPct(weightPct)
                    .build();
            newHoldings.add(holding);
        }

        userHoldingRepository.saveAll(newHoldings);

        ManualSelectionPortfolio portfolio = buildPortfolioResponse(newHoldings);

        // 5) Perform Portfolio Analysis
        List<Fund> analyzedFunds = newHoldings.stream().map(UserHolding::getFund).collect(Collectors.toList());
        Map<UUID, Double> weights = newHoldings.stream()
                .collect(Collectors.toMap(
                        h -> h.getFund().getFundId(),
                        h -> h.getWeightPct() / 100.0));

        var analysis = portfolioAnalyzerService.analyzePortfolio(analyzedFunds, weights);

        return ManualSelectionResponse.builder()
                .results(results)
                .portfolio(portfolio)
                .analysis(analysis)
                .build();
    }

    private void validateWeights(List<ManualSelectionItemRequest> selections) {
        int total = selections.stream()
                .map(ManualSelectionItemRequest::getWeightPct)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (total != 100) {
            throw new BadRequestException("Total weightPct must equal 100. Received: " + total);
        }
    }

    private void validateNoDuplicateFunds(Map<Integer, Fund> resolvedFundsByIndex, int expectedCount) {
        if (resolvedFundsByIndex.size() != expectedCount) {
            throw new BadRequestException("Some funds could not be resolved");
        }

        Set<UUID> uniqueFundIds = resolvedFundsByIndex.values().stream()
                .map(Fund::getFundId)
                .collect(Collectors.toSet());

        if (uniqueFundIds.size() != expectedCount) {
            throw new BadRequestException("Duplicate funds detected in selections after resolution");
        }
    }

    private record FundUpsertOutcome(Fund fund, boolean created) {
    }

    private FundUpsertOutcome upsertFundFromEtl(Map<String, Object> holding) {
        String isin = extractString(holding, "isin");
        String fundName = extractString(holding, "fundName");
        String amcName = extractString(holding, "amc");
        String fundCategory = extractString(holding, "category");
        Double expenseRatio = extractDouble(holding, "expenseRatio");
        Double currentNav = extractDouble(holding, "currentNav");
        java.time.LocalDate navAsOf = extractLocalDate(holding, "navAsOf");
        JsonNode sectorAllocation = extractJsonNode(holding, "sectorAllocation");
        JsonNode topHoldings = extractJsonNode(holding, "topHoldings");
        JsonNode fundMetadata = extractJsonNode(holding, "fundMetadata");

        if (isin == null || fundName == null) {
            throw new ServiceUnavailableException("ETL", "ETL response missing required fields (isin/fundName)");
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
            if (existingFund.getFundName() == null || existingFund.getFundName().isBlank()) {
                existingFund.setFundName(fundName);
            }

            return new FundUpsertOutcome(fundRepository.save(existingFund), false);
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

        return new FundUpsertOutcome(created, true);
    }

    private ManualSelectionPortfolio buildPortfolioResponse(List<UserHolding> userHoldings) {
        List<ManualSelectionHolding> holdings = userHoldings.stream()
                .map(uh -> {
                    Fund f = uh.getFund();
                    return ManualSelectionHolding.builder()
                            .fundId(f.getFundId())
                            .fundName(f.getFundName())
                            .isin(f.getIsin())
                            .amcName(f.getAmcName())
                            .fundCategory(f.getFundCategory())
                            .directPlan(f.getDirectPlan())
                            .currentNav(f.getCurrentNav())
                            .navAsOf(f.getNavAsOf())
                            .weightPct(uh.getWeightPct())
                            .sectorAllocation(f.getSectorAllocationJson())
                            .topHoldings(f.getTopHoldingsJson())
                            .fundMetadata(f.getFundMetadataJson())
                            .build();
                })
                .collect(Collectors.toList());

        int totalWeight = userHoldings.stream()
                .map(UserHolding::getWeightPct)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return ManualSelectionPortfolio.builder()
                .summary(ManualSelectionPortfolioSummary.builder()
                        .totalHoldings(holdings.size())
                        .totalWeightPct(totalWeight)
                        .build())
                .holdings(holdings)
                .build();
    }

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
            return null;
        }
    }

    private java.time.LocalDate extractLocalDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof java.time.LocalDate)
            return (java.time.LocalDate) value;
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof String s) {
            try {
                return java.time.LocalDate.parse(s);
            } catch (Exception ignored) {
            }
            try {
                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
                return java.time.LocalDate.parse(s, dtf);
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
