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
import com.mutualfunds.api.mutual_fund.service.analytics.PortfolioAnalyzerService;
import com.mutualfunds.api.mutual_fund.service.analytics.WealthProjectionService;

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
    private final PortfolioAnalyzerService portfolioAnalyzerService;
    private final WealthProjectionService wealthProjectionService;

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

            // Check if fundId (ISIN) or fundName exists in database
            Fund existingFund = null;
            String inputIsin = null;
            String inputFundName = null;

            if (item.getFundId() != null) {
                // fundId is actually ISIN value
                inputIsin = item.getFundId();
                existingFund = fundRepository.findByIsin(inputIsin).orElse(null);
            } else if (item.getFundName() != null) {
                inputFundName = item.getFundName().trim();
                final String finalInputFundName = inputFundName;
                // Try to find by exact fund name match
                List<Fund> matchingFunds = fundRepository.findByFundNameContainingIgnoreCase(inputFundName);
                if (!matchingFunds.isEmpty()) {
                    // Use the first exact match or closest match
                    existingFund = matchingFunds.stream()
                            .filter(f -> f.getFundName().equalsIgnoreCase(finalInputFundName))
                            .findFirst()
                            .orElse(matchingFunds.get(0));
                }
            }

            // Check if existing fund data is fresh (within a week)
            boolean isFreshData = existingFund != null &&
                    existingFund.getLastUpdated() != null &&
                    existingFund.getLastUpdated().isAfter(LocalDateTime.now().minusWeeks(1)) &&
                    existingFund.getCurrentNav() != null; // Require valid NAV — don't serve incomplete cached records

            if (isFreshData) {
                // Use existing fund from database
                resolvedFundsByIndex.put(i, existingFund);
                results.add(ManualSelectionResult.builder()
                        .inputFundId(item.getFundId())
                        .inputFundName(item.getFundName())
                        .status(STATUS_RESOLVED_FROM_DB)
                        .fundId(existingFund.getFundId())
                        .fundName(existingFund.getFundName())
                        .isin(existingFund.getIsin())
                        .message("Resolved from database (data is fresh)")
                        .build());
            } else {
                // Call ETL to get fresh data
                etlIndexes.add(i);
                String fundNameForEtl = inputFundName != null ? inputFundName
                        : (existingFund != null ? existingFund.getFundName() : "Unknown");
                etlRequests.add(ParsedHoldingEntry.builder()
                        .fundName(fundNameForEtl)
                        .units(1.0)
                        .build());
                results.add(ManualSelectionResult.builder()
                        .inputFundId(item.getFundId())
                        .inputFundName(item.getFundName())
                        .status(STATUS_ENRICHED_FROM_ETL)
                        .message(existingFund != null ? "Refreshing from ETL (data is stale)"
                                : "Fetching from ETL (new fund)")
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

            // Build a name-based map so we can match enriched results back to each
            // requested fund individually, without requiring a 1:1 positional alignment.
            // This handles partial ETL results (status="partial") gracefully.
            Map<String, Map<String, Object>> enrichedByName = new java.util.HashMap<>();
            if (enrichedData != null) {
                for (Map<String, Object> em : enrichedData) {
                    Object fn = em.get("fund_name");
                    if (fn == null)
                        fn = em.get("fundName");
                    if (fn != null) {
                        enrichedByName.put(fn.toString().toLowerCase().trim(), em);
                    }
                }
            }

            for (int j = 0; j < etlRequests.size(); j++) {
                int selectionIndex = etlIndexes.get(j);
                String requestedName = etlRequests.get(j).getFundName().toLowerCase().trim();
                Map<String, Object> enrichedMap = enrichedByName.get(requestedName);

                if (enrichedMap == null) {
                    // This specific fund failed enrichment — mark it and continue with others
                    log.warn("ETL could not enrich fund '{}' (index {}). Marking as failed.",
                            etlRequests.get(j).getFundName(), selectionIndex);
                    ManualSelectionResult failedResult = results.get(selectionIndex);
                    failedResult.setStatus("ENRICHMENT_FAILED");
                    failedResult.setMessage(
                            "ETL could not resolve this fund. It has been excluded and the remaining " +
                                    "funds' weights have been re-normalised to 100%.");
                    continue;
                }

                FundUpsertOutcome upsertOutcome = upsertFundFromEtl(enrichedMap);
                resolvedFundsByIndex.put(selectionIndex, upsertOutcome.fund());

                ManualSelectionResult existing = results.get(selectionIndex);
                existing.setFundId(upsertOutcome.fund().getFundId());
                existing.setFundName(upsertOutcome.fund().getFundName());
                existing.setIsin(upsertOutcome.fund().getIsin());
                existing.setStatus(upsertOutcome.created() ? STATUS_CREATED_FROM_ETL : STATUS_ENRICHED_FROM_ETL);
            }
        }

        // 3) Collect failed indexes and validate no duplicates among resolved funds
        Set<Integer> failedIndexes = new java.util.HashSet<>();
        for (int i = 0; i < selections.size(); i++) {
            if (resolvedFundsByIndex.get(i) == null) {
                failedIndexes.add(i);
            }
        }
        validateNoDuplicateFunds(resolvedFundsByIndex, selections.size(), failedIndexes);

        // 4) Replace holdings + allocations atomically
        userHoldingRepository.deleteByUser_UserId(userId);

        // Ensure deletes are executed before the following batch insert.
        // Without this, Hibernate may order INSERTs before DELETEs within the same
        // transaction, causing (user_id, fund_id) unique constraint violations.
        userHoldingRepository.flush();

        List<UserHolding> newHoldings = new ArrayList<>(selections.size());

        for (int i = 0; i < selections.size(); i++) {
            Fund fund = resolvedFundsByIndex.get(i);
            if (fund == null) {
                // Fund failed ETL enrichment — already flagged in results, skip it
                log.warn("Skipping holding at index {} — fund could not be resolved.", i);
                continue;
            }
            Integer weightPct = weightPctByIndex.get(i);
            UserHolding holding = UserHolding.builder()
                    .user(currentUser)
                    .fund(fund)
                    .weightPct(weightPct)
                    .build();
            newHoldings.add(holding);
        }

        userHoldingRepository.saveAll(newHoldings);

        // 5) Re-normalise weights if any funds were skipped due to enrichment failure.
        // Without this, the saved weights sum to less than 100%, which skews analytics.
        if (!failedIndexes.isEmpty() && !newHoldings.isEmpty()) {
            int resolvedTotalWeight = newHoldings.stream()
                    .mapToInt(h -> h.getWeightPct() != null ? h.getWeightPct() : 0)
                    .sum();
            if (resolvedTotalWeight > 0 && resolvedTotalWeight != 100) {
                log.warn("Re-normalising weights from {}% to 100% after {} fund(s) failed enrichment.",
                        resolvedTotalWeight, failedIndexes.size());
                int runningTotal = 0;
                for (int k = 0; k < newHoldings.size(); k++) {
                    UserHolding h = newHoldings.get(k);
                    if (k == newHoldings.size() - 1) {
                        // Assign remainder to the last fund to avoid rounding drift
                        h.setWeightPct(100 - runningTotal);
                    } else {
                        int normalised = (int) Math.round(h.getWeightPct() * 100.0 / resolvedTotalWeight);
                        h.setWeightPct(normalised);
                        runningTotal += normalised;
                    }
                }
                // Persist the updated weights
                userHoldingRepository.saveAll(newHoldings);
            }
        }

        ManualSelectionPortfolio portfolio = buildPortfolioResponse(newHoldings);

        // 5) Perform Portfolio Analysis
        List<Fund> analyzedFunds = newHoldings.stream().map(UserHolding::getFund).collect(Collectors.toList());
        Map<UUID, Double> weights = newHoldings.stream()
                .collect(Collectors.toMap(
                        h -> h.getFund().getFundId(),
                        h -> h.getWeightPct() / 100.0));

        var analysis = portfolioAnalyzerService.analyzePortfolio(analyzedFunds, weights);

        // Wealth Projection (Existing Portfolio)
        // Assume default ₹1 Lakh relative illustration for now, or user-specific if
        // available.
        // The DTO returns relative growth, so the start amount is a scaler.
        var projection = wealthProjectionService.calculateProjection(analyzedFunds, weights, 100000.0, 10);
        analysis.setWealthProjection(projection);

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

    private void validateNoDuplicateFunds(Map<Integer, Fund> resolvedFundsByIndex, int totalCount,
            Set<Integer> failedIndexes) {
        // Only resolved (non-failed) funds count toward the expected size
        int expectedResolved = totalCount - failedIndexes.size();
        if (resolvedFundsByIndex.size() != expectedResolved) {
            throw new BadRequestException("Some funds could not be resolved after ETL enrichment");
        }

        // Duplicate check only applies to funds that were resolved
        Set<UUID> uniqueFundIds = resolvedFundsByIndex.values().stream()
                .map(Fund::getFundId)
                .collect(Collectors.toSet());

        if (uniqueFundIds.size() != expectedResolved) {
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
        // Require a valid NAV — a fund without NAV means enrichment was incomplete
        // and should not be written to the DB.
        if (currentNav == null) {
            throw new ServiceUnavailableException("ETL",
                    "ETL enrichment for '" + fundName + "' is incomplete: currentNav is null. " +
                            "The fund will not be saved. Please retry.");
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
