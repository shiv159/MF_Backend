package com.mutualfunds.api.mutual_fund.features.portfolio.manual.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundUpsertService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.portfolio.analytics.application.PortfolioAnalyzerService;
import com.mutualfunds.api.mutual_fund.features.portfolio.analytics.application.WealthProjectionService;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.persistence.UserHoldingRepository;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.api.IManualSelectionService;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionItemRequest;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionPortfolio;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionPortfolioSummary;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionRequest;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionResponse;
import com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto.ManualSelectionResult;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.api.IOnboardingService;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.application.ETLEnrichmentService;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.dto.EnrichmentResult;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.dto.request.ParsedHoldingEntry;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.shared.exception.BadRequestException;
import com.mutualfunds.api.mutual_fund.shared.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualSelectionService implements IManualSelectionService {

    private static final String STATUS_RESOLVED_FROM_DB = "RESOLVED_FROM_DB";
    private static final String STATUS_CREATED_FROM_ETL = "CREATED_FROM_ETL";
    private static final String STATUS_ENRICHED_FROM_ETL = "ENRICHED_FROM_ETL";
    private static final String STATUS_ENRICHMENT_FAILED = "ENRICHMENT_FAILED";
    private static final int DB_FRESHNESS_DAYS = 7;

    private final IOnboardingService onboardingService;
    private final FundQueryService fundQueryService;
    private final FundUpsertService fundUpsertService;
    private final UserHoldingRepository userHoldingRepository;
    private final ETLEnrichmentService etlEnrichmentService;
    private final PortfolioAnalyzerService portfolioAnalyzerService;
    private final WealthProjectionService wealthProjectionService;

    @Override
    @Transactional
    public ManualSelectionResponse replaceHoldingsWithManualSelection(ManualSelectionRequest request) {
        User currentUser = onboardingService.getCurrentUser();
        UUID userId = currentUser.getUserId();

        List<ManualSelectionItemRequest> selections = request.getSelections();
        validateWeights(selections);

        List<ManualSelectionResult> results = new ArrayList<>();
        Map<Integer, Fund> resolvedFundsByIndex = new HashMap<>();
        Map<Integer, Integer> weightPctByIndex = new HashMap<>();

        List<Integer> etlIndexes = new ArrayList<>();
        List<ParsedHoldingEntry> etlRequests = new ArrayList<>();

        for (int i = 0; i < selections.size(); i++) {
            ManualSelectionItemRequest item = selections.get(i);
            weightPctByIndex.put(i, item.getWeightPct());

            ResolvedSelection resolvedSelection = resolveSelection(item);
            Fund existingFund = resolvedSelection.fund();
            ReuseDecision reuseDecision = evaluateReuse(existingFund);

            if (reuseDecision.reusable()) {
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
                continue;
            }

            String fallbackReason = reuseDecision.reason();
            String fundNameForEtl = firstNonBlank(
                    resolvedSelection.inputFundName(),
                    existingFund != null ? existingFund.getFundName() : null);

            if (fundNameForEtl == null) {
                log.warn("Unable to route selection index {} to ETL because no fund name is available. Reason={}", i,
                        fallbackReason);
                results.add(ManualSelectionResult.builder()
                        .inputFundId(item.getFundId())
                        .inputFundName(item.getFundName())
                        .status(STATUS_ENRICHMENT_FAILED)
                        .message("Unable to resolve this fund: " + formatFallbackReason(fallbackReason))
                        .build());
                continue;
            }

            log.info("Routing selection index {} for '{}' to ETL. Reason={}", i, fundNameForEtl, fallbackReason);
            etlIndexes.add(i);
            etlRequests.add(ParsedHoldingEntry.builder()
                    .fundName(fundNameForEtl)
                    .units(1.0)
                    .build());
            results.add(ManualSelectionResult.builder()
                    .inputFundId(item.getFundId())
                    .inputFundName(item.getFundName())
                    .status(STATUS_ENRICHED_FROM_ETL)
                    .message(buildFallbackMessage(existingFund, fallbackReason))
                    .build());
        }

        if (!etlRequests.isEmpty()) {
            EnrichmentResult enrichmentResult;
            try {
                enrichmentResult = etlEnrichmentService.enrichPortfolioData(etlRequests, userId, "manual");
            } catch (Exception e) {
                throw new ServiceUnavailableException("ETL", "Failed to enrich funds: " + e.getMessage());
            }

            List<Map<String, Object>> enrichedData = enrichmentResult.getEnrichedData();

            Map<String, Map<String, Object>> enrichedByName = new HashMap<>();
            if (enrichedData != null) {
                for (Map<String, Object> enrichedMap : enrichedData) {
                    Object inputName = enrichedMap.get("input_fund_name");
                    if (inputName == null) {
                        inputName = enrichedMap.get("inputFundName");
                    }
                    Object fundName = enrichedMap.get("fund_name");
                    Object camelFundName = enrichedMap.get("fundName");
                    Object key = inputName != null ? inputName : (fundName != null ? fundName : camelFundName);
                    if (key != null) {
                        enrichedByName.put(key.toString().toLowerCase().trim(), enrichedMap);
                    }
                }
            }

            for (int j = 0; j < etlRequests.size(); j++) {
                int selectionIndex = etlIndexes.get(j);
                String requestedName = etlRequests.get(j).getFundName().toLowerCase().trim();
                Map<String, Object> enrichedMap = enrichedByName.get(requestedName);

                if (enrichedMap == null) {
                    log.warn("ETL could not enrich fund '{}' (index {}). Marking as failed.",
                            etlRequests.get(j).getFundName(), selectionIndex);
                    ManualSelectionResult failedResult = results.get(selectionIndex);
                    failedResult.setStatus(STATUS_ENRICHMENT_FAILED);
                    failedResult.setMessage(
                            "ETL could not resolve this fund. It has been excluded and the remaining funds' weights have been re-normalised to 100%.");
                    continue;
                }

                ManualSelectionResult result = results.get(selectionIndex);
                try {
                    FundUpsertService.FundUpsertResult upsertOutcome = fundUpsertService.upsertFromEnriched(enrichedMap,
                            true);
                    resolvedFundsByIndex.put(selectionIndex, upsertOutcome.fund());
                    result.setFundId(upsertOutcome.fund().getFundId());
                    result.setFundName(upsertOutcome.fund().getFundName());
                    result.setIsin(upsertOutcome.fund().getIsin());
                    result.setStatus(upsertOutcome.created() ? STATUS_CREATED_FROM_ETL : STATUS_ENRICHED_FROM_ETL);
                } catch (ServiceUnavailableException e) {
                    log.warn("ETL returned incomplete data for selection index {} and fund '{}': {}",
                            selectionIndex, etlRequests.get(j).getFundName(), e.getMessage());
                    result.setStatus(STATUS_ENRICHMENT_FAILED);
                    result.setMessage(
                            "ETL returned incomplete data for this fund. It has been excluded and the remaining funds' weights have been re-normalised to 100%.");
                }
            }
        }

        Set<Integer> failedIndexes = new HashSet<>();
        for (int i = 0; i < selections.size(); i++) {
            if (resolvedFundsByIndex.get(i) == null) {
                failedIndexes.add(i);
            }
        }
        validateNoDuplicateFunds(resolvedFundsByIndex, selections.size(), failedIndexes);

        userHoldingRepository.deleteByUser_UserId(userId);
        userHoldingRepository.flush();

        List<UserHolding> newHoldings = new ArrayList<>(selections.size());

        for (int i = 0; i < selections.size(); i++) {
            Fund fund = resolvedFundsByIndex.get(i);
            if (fund == null) {
                log.warn("Skipping holding at index {} because fund could not be resolved.", i);
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

        if (!failedIndexes.isEmpty() && !newHoldings.isEmpty()) {
            int resolvedTotalWeight = newHoldings.stream()
                    .mapToInt(h -> h.getWeightPct() != null ? h.getWeightPct() : 0)
                    .sum();
            if (resolvedTotalWeight > 0 && resolvedTotalWeight != 100) {
                log.warn("Re-normalising weights from {}% to 100% after {} fund(s) failed enrichment.",
                        resolvedTotalWeight, failedIndexes.size());
                int runningTotal = 0;
                for (int k = 0; k < newHoldings.size(); k++) {
                    UserHolding holding = newHoldings.get(k);
                    if (k == newHoldings.size() - 1) {
                        holding.setWeightPct(100 - runningTotal);
                    } else {
                        int normalised = (int) Math.round(holding.getWeightPct() * 100.0 / resolvedTotalWeight);
                        holding.setWeightPct(normalised);
                        runningTotal += normalised;
                    }
                }
                userHoldingRepository.saveAll(newHoldings);
            }
        }

        ManualSelectionPortfolio portfolio = buildPortfolioResponse(newHoldings);

        List<Fund> analyzedFunds = newHoldings.stream().map(UserHolding::getFund).collect(Collectors.toList());
        Map<UUID, Double> weights = newHoldings.stream()
                .collect(Collectors.toMap(
                        h -> h.getFund().getFundId(),
                        h -> h.getWeightPct() / 100.0));

        var analysis = portfolioAnalyzerService.analyzePortfolio(analyzedFunds, weights);
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

    private ResolvedSelection resolveSelection(ManualSelectionItemRequest item) {
        String inputFundId = normalise(item.getFundId());
        String inputFundName = normalise(item.getFundName());
        Fund existingFund = null;

        if (inputFundId != null) {
            try {
                existingFund = fundQueryService.findById(UUID.fromString(inputFundId)).orElse(null);
            } catch (IllegalArgumentException ignored) {
                existingFund = fundQueryService.findByIsin(inputFundId).orElse(null);
            }
        }

        if (existingFund == null && inputFundName != null) {
            final String exactName = inputFundName;
            List<Fund> matchingFunds = fundQueryService.findByFundNameContainingIgnoreCase(inputFundName);
            if (!matchingFunds.isEmpty()) {
                existingFund = matchingFunds.stream()
                        .filter(fund -> fund.getFundName().equalsIgnoreCase(exactName))
                        .findFirst()
                        .orElse(matchingFunds.get(0));
            }
        }

        return new ResolvedSelection(existingFund, inputFundId, inputFundName);
    }

    private ReuseDecision evaluateReuse(Fund fund) {
        if (fund == null) {
            return new ReuseDecision(false, "unresolved_identifier");
        }
        if (fund.getLastUpdated() == null || fund.getLastUpdated().isBefore(LocalDateTime.now().minusDays(DB_FRESHNESS_DAYS))) {
            return new ReuseDecision(false, "stale_last_updated");
        }
        if (fund.getCurrentNav() == null) {
            return new ReuseDecision(false, "missing_nav");
        }
        if (isMissing(fund.getSectorAllocationJson())) {
            return new ReuseDecision(false, "missing_sector_allocation");
        }
        if (isMissing(fund.getTopHoldingsJson())) {
            return new ReuseDecision(false, "missing_top_holdings");
        }
        if (isMissing(fund.getFundMetadataJson())) {
            return new ReuseDecision(false, "missing_fund_metadata");
        }
        if (hasMetadataQualityIssues(fund.getFundMetadataJson())) {
            return new ReuseDecision(false, "missing_fund_metadata");
        }
        return new ReuseDecision(true, "reusable");
    }

    private boolean isMissing(JsonNode node) {
        return node == null || node.isNull() || node.isEmpty();
    }

    private boolean hasMetadataQualityIssues(JsonNode fundMetadata) {
        JsonNode qualityNode = fundMetadata.path("data_quality");
        if (qualityNode.isMissingNode() || qualityNode.isNull() || qualityNode.isEmpty()) {
            qualityNode = fundMetadata.path("mstarpy_metadata").path("data_quality");
        }

        return hasTextValues(qualityNode.path("missing_fields")) || hasTextValues(qualityNode.path("quality_flags"));
    }

    private boolean hasTextValues(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return false;
        }
        for (JsonNode value : node) {
            if (value.isTextual() && !value.asText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private void validateNoDuplicateFunds(Map<Integer, Fund> resolvedFundsByIndex, int totalCount,
            Set<Integer> failedIndexes) {
        int expectedResolved = totalCount - failedIndexes.size();
        if (resolvedFundsByIndex.size() != expectedResolved) {
            throw new BadRequestException("Some funds could not be resolved after ETL enrichment");
        }

        Set<UUID> uniqueFundIds = resolvedFundsByIndex.values().stream()
                .map(Fund::getFundId)
                .collect(Collectors.toSet());

        if (uniqueFundIds.size() != expectedResolved) {
            throw new BadRequestException("Duplicate funds detected in selections after resolution");
        }
    }

    private String buildFallbackMessage(Fund existingFund, String reason) {
        if (existingFund == null) {
            return "Fetching from ETL (" + formatFallbackReason(reason) + ")";
        }
        return "Refreshing from ETL (" + formatFallbackReason(reason) + ")";
    }

    private String formatFallbackReason(String reason) {
        return switch (reason) {
            case "stale_last_updated" -> "data is stale";
            case "missing_nav" -> "cached data is missing NAV";
            case "missing_sector_allocation" -> "cached data is missing sector allocation";
            case "missing_top_holdings" -> "cached data is missing top holdings";
            case "missing_fund_metadata" -> "cached data is missing fund metadata";
            case "unresolved_identifier" -> "identifier could not be resolved in database";
            default -> "cached data is incomplete";
        };
    }

    private String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private ManualSelectionPortfolio buildPortfolioResponse(List<UserHolding> userHoldings) {
        List<ManualSelectionHolding> holdings = userHoldings.stream()
                .map(userHolding -> {
                    Fund fund = userHolding.getFund();
                    return ManualSelectionHolding.builder()
                            .fundId(fund.getFundId())
                            .fundName(fund.getFundName())
                            .isin(fund.getIsin())
                            .amcName(fund.getAmcName())
                            .fundCategory(fund.getFundCategory())
                            .directPlan(fund.getDirectPlan())
                            .currentNav(fund.getCurrentNav())
                            .navAsOf(fund.getNavAsOf())
                            .weightPct(userHolding.getWeightPct())
                            .sectorAllocation(fund.getSectorAllocationJson())
                            .topHoldings(fund.getTopHoldingsJson())
                            .fundMetadata(fund.getFundMetadataJson())
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

    private record ResolvedSelection(Fund fund, String inputFundId, String inputFundName) {
    }

    private record ReuseDecision(boolean reusable, String reason) {
    }
}
