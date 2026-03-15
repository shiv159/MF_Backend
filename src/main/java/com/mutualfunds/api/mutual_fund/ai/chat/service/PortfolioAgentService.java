package com.mutualfunds.api.mutual_fund.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.ai.chat.dto.ChatAction;
import com.mutualfunds.api.mutual_fund.ai.chat.dto.ChatMessageRequest;
import com.mutualfunds.api.mutual_fund.ai.chat.dto.ChatSource;
import com.mutualfunds.api.mutual_fund.ai.chat.dto.ChatStreamEvent;
import com.mutualfunds.api.mutual_fund.ai.chat.model.ChatIntent;
import com.mutualfunds.api.mutual_fund.dto.analytics.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.RiskInsightsDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.RollingReturnsDTO;
import com.mutualfunds.api.mutual_fund.dto.risk.RiskProfileResponse;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.entity.UserHolding;
import com.mutualfunds.api.mutual_fund.repository.FundRepository;
import com.mutualfunds.api.mutual_fund.repository.UserHoldingRepository;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import com.mutualfunds.api.mutual_fund.service.PortfolioDataQualityInspector;
import com.mutualfunds.api.mutual_fund.service.analytics.FundAnalyticsService;
import com.mutualfunds.api.mutual_fund.service.analytics.PortfolioDiagnosticService;
import com.mutualfunds.api.mutual_fund.service.risk.RiskRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAgentService {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\b(?:vs|versus|compare|and)\\b|,");

    private final IntentRouterService intentRouterService;
    private final ChatSynthesisService chatSynthesisService;
    private final UserHoldingRepository userHoldingRepository;
    private final UserRepository userRepository;
    private final FundRepository fundRepository;
    private final PortfolioDiagnosticService portfolioDiagnosticService;
    private final RiskRecommendationService riskRecommendationService;
    private final FundAnalyticsService fundAnalyticsService;
    private final PortfolioDataQualityInspector dataQualityInspector;
    private final ObjectMapper objectMapper;

    public Flux<ChatStreamEvent> streamMessage(UUID userId, ChatMessageRequest request) {
        return Flux.<ChatStreamEvent>create(sink -> Schedulers.boundedElastic().schedule(() -> {
            try {
                process(userId, request, sink::next);
                sink.complete();
            } catch (Exception ex) {
                log.error("Streaming chat failed", ex);
                sink.next(event("error", null, null,
                        objectNode("message", ex.getMessage() == null ? "Unable to process request" : ex.getMessage())));
                sink.complete();
            }
        }), FluxSink.OverflowStrategy.BUFFER);
    }

    private void process(UUID userId, ChatMessageRequest request, Consumer<ChatStreamEvent> emitter) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }

        ChatIntent intent = intentRouterService.resolveIntent(message, request.getScreenContext());
        UUID conversationId = resolveConversationId(request.getConversationId());
        UUID assistantMessageId = UUID.randomUUID();

        emit(emitter, event("status", conversationId, null,
                objectNode("status", request.getConversationId() == null ? "conversation_started" : "conversation_resumed")));
        emit(emitter, event("status", conversationId, null,
                objectNode("status", "intent_resolved", "intent", intent.name())));

        List<UserHolding> holdings = userHoldingRepository.findByUserIdWithFund(userId);
        PortfolioDataQualityInspector.Result qualityResult = dataQualityInspector.inspect(holdings);

        List<String> warnings = new ArrayList<>(qualityResult.warnings());
        List<ChatSource> sources = new ArrayList<>();
        List<ChatAction> actions = new ArrayList<>();
        ObjectNode toolTrace = objectMapper.createObjectNode();

        emit(emitter, toolStart(conversationId, "portfolio_snapshot"));
        ObjectNode portfolioSnapshot = buildPortfolioSnapshot(holdings);
        toolTrace.set("portfolioSnapshot", portfolioSnapshot);
        sources.add(ChatSource.builder()
                .label("Portfolio holdings")
                .type("PORTFOLIO")
                .entityId(userId.toString())
                .build());
        emit(emitter, toolResult(conversationId, "portfolio_snapshot", portfolioSnapshot));

        switch (intent) {
            case DATA_QUALITY -> handleDataQuality(conversationId, qualityResult, toolTrace, sources, emitter);
            case FUND_COMPARE -> handleFundCompare(message, holdings, toolTrace, sources, warnings, emitter);
            case FUND_RISK -> handleFundRisk(message, holdings, toolTrace, sources, warnings, emitter);
            case FUND_PERFORMANCE -> handleFundPerformance(message, holdings, toolTrace, sources, warnings, emitter);
            case RISK_PROFILE_EXPLAINER -> handleRiskProfile(conversationId, userId, toolTrace, sources,
                    warnings, emitter);
            case DIAGNOSTIC_EXPLAINER -> handleDiagnostic(conversationId, userId, toolTrace, sources,
                    warnings, emitter);
            case PORTFOLIO_SUMMARY -> handlePortfolioSummary(conversationId, userId, toolTrace, sources,
                    warnings, emitter);
            case GENERAL_QA -> handleGeneralQuestion(conversationId, userId, toolTrace, sources, warnings,
                    emitter);
            case REBALANCE_DRAFT -> handleRebalanceDraft(conversationId, userId, holdings, qualityResult,
                    toolTrace, sources, warnings, actions, emitter);
        }

        ChatSynthesisService.SynthesisResult synthesis = chatSynthesisService.synthesize(
                intent, conversationId.toString(), request.getScreenContext(), message, toolTrace, warnings);

        boolean requiresConfirmation = actions.stream().anyMatch(action -> "REBALANCE_DRAFT".equals(action.getType()));
        streamContent(emitter, conversationId, assistantMessageId, synthesis.response());
        emit(emitter, event("message_complete", conversationId, assistantMessageId,
                objectNode("intent", intent.name(), "sources", objectMapper.valueToTree(sources),
                        "warnings", objectMapper.valueToTree(warnings),
                        "actions", objectMapper.valueToTree(actions),
                        "requiresConfirmation", requiresConfirmation)));
    }

    private void handleDataQuality(UUID conversationId,
            PortfolioDataQualityInspector.Result qualityResult,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            Consumer<ChatStreamEvent> emitter) {
        emit(emitter, toolStart(conversationId, "data_quality"));
        ObjectNode qualityNode = objectMapper.createObjectNode();
        qualityNode.put("staleCount", qualityResult.staleCount());
        qualityNode.put("missingCount", qualityResult.missingCount());
        qualityNode.put("freshCount", qualityResult.freshCount());
        qualityNode.set("warnings", objectMapper.valueToTree(qualityResult.warnings()));
        qualityNode.set("staleFunds", objectMapper.valueToTree(qualityResult.staleFunds()));
        qualityNode.set("missingFunds", objectMapper.valueToTree(qualityResult.missingFunds()));
        toolTrace.set("dataQuality", qualityNode);
        sources.add(ChatSource.builder().label("Data quality checks").type("DATA_QUALITY").entityId(null).build());
        emit(emitter, toolResult(conversationId, "data_quality", qualityNode));
    }

    private void handleFundCompare(String message,
            List<UserHolding> holdings,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        List<Fund> funds = resolveRelevantFunds(message, holdings, 2);
        emitFundTool("fund_compare", funds, toolTrace, sources, warnings, emitter, true, true);
    }

    private void handleFundRisk(String message,
            List<UserHolding> holdings,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        List<Fund> funds = resolveRelevantFunds(message, holdings, 2);
        emitFundTool("fund_risk", funds, toolTrace, sources, warnings, emitter, false, true);
    }

    private void handleFundPerformance(String message,
            List<UserHolding> holdings,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        List<Fund> funds = resolveRelevantFunds(message, holdings, 2);
        emitFundTool("fund_performance", funds, toolTrace, sources, warnings, emitter, true, false);
    }

    private void handleRiskProfile(UUID conversationId,
            UUID userId,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        emit(emitter, toolStart(conversationId, "risk_profile"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getRiskTolerance() == null || user.getInvestmentHorizonYears() == null) {
            warnings.add("Risk profile is incomplete, so the allocation guidance may be limited.");
            toolTrace.set("riskProfile", objectNode("status", "missing_profile"));
            emit(emitter, toolResult(conversationId, "risk_profile", toolTrace.path("riskProfile")));
            return;
        }

        RiskProfileResponse response = riskRecommendationService.generateRecommendation(user);
        toolTrace.set("riskProfile", objectMapper.valueToTree(response));
        sources.add(ChatSource.builder()
                .label("Risk profile")
                .type("RISK_PROFILE")
                .entityId(userId.toString())
                .build());
        emit(emitter, toolResult(conversationId, "risk_profile", toolTrace.path("riskProfile")));
    }

    private void handleDiagnostic(UUID conversationId,
            UUID userId,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        emit(emitter, toolStart(conversationId, "portfolio_diagnostic"));
        PortfolioDiagnosticDTO diagnostic = portfolioDiagnosticService.runDiagnostic(userId);
        toolTrace.set("diagnostic", objectMapper.valueToTree(diagnostic));
        long highSeverityCount = diagnostic.getSuggestions().stream()
                .filter(suggestion -> suggestion.getSeverity() == PortfolioDiagnosticDTO.Severity.HIGH)
                .count();
        if (highSeverityCount > 0) {
            warnings.add("Your diagnostic includes " + highSeverityCount + " high-severity portfolio issues.");
        }
        sources.add(ChatSource.builder()
                .label("Portfolio diagnostic")
                .type("DIAGNOSTIC")
                .entityId(userId.toString())
                .build());
        emit(emitter, toolResult(conversationId, "portfolio_diagnostic", toolTrace.path("diagnostic")));
    }

    private void handlePortfolioSummary(UUID conversationId,
            UUID userId,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        handleDiagnostic(conversationId, userId, toolTrace, sources, warnings, emitter);
    }

    private void handleGeneralQuestion(UUID conversationId,
            UUID userId,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        handleDiagnostic(conversationId, userId, toolTrace, sources, warnings, emitter);
        if (!toolTrace.has("riskProfile")) {
            handleRiskProfile(conversationId, userId, toolTrace, sources, warnings, emitter);
        }
    }

    private void handleRebalanceDraft(UUID conversationId,
            UUID userId,
            List<UserHolding> holdings,
            PortfolioDataQualityInspector.Result qualityResult,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            List<ChatAction> actions,
            Consumer<ChatStreamEvent> emitter) {
        emit(emitter, toolStart(conversationId, "rebalance_draft"));

        PortfolioDiagnosticDTO diagnostic = portfolioDiagnosticService.runDiagnostic(userId);
        toolTrace.set("diagnostic", objectMapper.valueToTree(diagnostic));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        RiskProfileResponse riskProfile = null;
        if (user.getRiskTolerance() != null && user.getInvestmentHorizonYears() != null) {
            riskProfile = riskRecommendationService.generateRecommendation(user);
            toolTrace.set("riskProfile", objectMapper.valueToTree(riskProfile));
        } else {
            warnings.add("Risk profile data is incomplete, so this rebalance draft stays at category level.");
        }

        ObjectNode draft = buildRebalanceDraft(holdings, diagnostic, riskProfile, qualityResult, warnings);
        toolTrace.set("rebalanceDraft", draft);
        toolTrace.put("draftSummary", buildDraftSummary(draft));

        actions.add(ChatAction.builder()
                .type("REBALANCE_DRAFT")
                .label("Review draft")
                .payload(draft)
                .build());
        actions.add(followUpAction("Explain why this shift helps", "Explain why this shift helps"));
        actions.add(followUpAction("Show a more conservative version", "Show a more conservative version of this rebalance draft"));

        sources.add(ChatSource.builder()
                .label("Rebalance planner")
                .type("REBALANCE")
                .entityId(userId.toString())
                .build());
        emit(emitter, toolResult(conversationId, "rebalance_draft", draft));
    }

    private ObjectNode buildRebalanceDraft(List<UserHolding> holdings,
            PortfolioDiagnosticDTO diagnostic,
            RiskProfileResponse riskProfile,
            PortfolioDataQualityInspector.Result qualityResult,
            List<String> warnings) {
        ObjectNode draft = objectMapper.createObjectNode();
        Map<String, Double> currentAllocation = new LinkedHashMap<>();
        currentAllocation.putAll(diagnostic.getMetrics().getAssetClassBreakdown());

        Map<String, Double> targetAllocation = new LinkedHashMap<>();
        if (riskProfile != null && riskProfile.getAssetAllocation() != null) {
            targetAllocation.put("Equity", riskProfile.getAssetAllocation().getEquity());
            targetAllocation.put("Debt", riskProfile.getAssetAllocation().getDebt());
            targetAllocation.put("Gold", riskProfile.getAssetAllocation().getGold());
        } else {
            targetAllocation.putAll(currentAllocation);
            if (!currentAllocation.containsKey("Debt") || currentAllocation.getOrDefault("Debt", 0.0) < 15.0) {
                targetAllocation.put("Debt", 20.0);
                targetAllocation.put("Equity", Math.max(0.0, 100.0 - targetAllocation.get("Debt")));
            }
        }

        ArrayNode proposedReductions = objectMapper.createArrayNode();
        ArrayNode proposedAdditions = objectMapper.createArrayNode();

        for (Map.Entry<String, Double> entry : targetAllocation.entrySet()) {
            String key = entry.getKey();
            double current = currentAllocation.getOrDefault(key, 0.0);
            double target = entry.getValue();
            double delta = roundTwoDecimals(target - current);
            if (delta > 5.0) {
                ObjectNode addition = objectMapper.createObjectNode();
                addition.put("category", key);
                addition.put("current", current);
                addition.put("target", target);
                addition.put("change", delta);
                addition.set("suggestedFunds", buildSuggestedFundsForCategory(key, holdings));
                proposedAdditions.add(addition);
            } else if (delta < -5.0) {
                ObjectNode reduction = objectMapper.createObjectNode();
                reduction.put("category", key);
                reduction.put("current", current);
                reduction.put("target", target);
                reduction.put("change", Math.abs(delta));
                proposedReductions.add(reduction);
            }
        }

        if (proposedAdditions.isEmpty() && proposedReductions.isEmpty()) {
            warnings.add("Current allocation is already close to the recommended mix, so the draft focuses on fund quality.");
        }

        draft.set("currentAllocationSummary", objectMapper.valueToTree(currentAllocation));
        draft.set("targetAllocationSummary", objectMapper.valueToTree(targetAllocation));
        draft.set("proposedReductions", proposedReductions);
        draft.set("proposedAdditions", proposedAdditions);
        draft.set("rationale", objectMapper.valueToTree(buildRebalanceRationale(diagnostic, qualityResult)));
        draft.put("expectedRiskShift", expectedRiskShift(currentAllocation, targetAllocation));
        draft.put("expectedDiversificationImprovement", expectedDiversificationImprovement(diagnostic));
        draft.put("requiresConfirmation", true);
        return draft;
    }

    private ArrayNode buildSuggestedFundsForCategory(String category, List<UserHolding> holdings) {
        Set<UUID> heldFundIds = holdings.stream()
                .map(UserHolding::getFund)
                .filter(java.util.Objects::nonNull)
                .map(Fund::getFundId)
                .collect(Collectors.toSet());

        List<Fund> alternatives = findAlternativeFunds(category, heldFundIds, 3);
        ArrayNode results = objectMapper.createArrayNode();
        if (alternatives.isEmpty()) {
            ObjectNode categoryOnly = objectMapper.createObjectNode();
            categoryOnly.put("category", category);
            categoryOnly.put("note", "No strong fund-level match found, so keep this as category guidance.");
            results.add(categoryOnly);
            return results;
        }

        for (Fund fund : alternatives) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("fundId", fund.getFundId().toString());
            node.put("fundName", fund.getFundName());
            node.put("category", fund.getFundCategory());
            if (fund.getExpenseRatio() != null) {
                node.put("expenseRatio", fund.getExpenseRatio());
            }
            if (fund.getCurrentNav() != null) {
                node.put("currentNav", fund.getCurrentNav());
            }
            node.put("fresh", isFreshFund(fund));
            results.add(node);
        }
        return results;
    }

    private List<Fund> findAlternativeFunds(String category, Set<UUID> heldFundIds, int limit) {
        return fundRepository.findAll().stream()
                .filter(fund -> fund.getFundId() != null && !heldFundIds.contains(fund.getFundId()))
                .filter(fund -> matchesCategory(category, fund.getFundCategory()))
                .filter(this::hasEnoughDataForSuggestion)
                .sorted(Comparator
                        .comparing((Fund fund) -> !Boolean.TRUE.equals(fund.getDirectPlan()))
                        .thenComparing(fund -> fund.getExpenseRatio() == null ? Double.MAX_VALUE : fund.getExpenseRatio())
                        .thenComparing(Fund::getFundName))
                .limit(limit)
                .toList();
    }

    private boolean matchesCategory(String targetCategory, String fundCategory) {
        if (targetCategory == null || fundCategory == null) {
            return false;
        }
        String normalizedTarget = targetCategory.toLowerCase(Locale.ROOT);
        String normalizedCategory = fundCategory.toLowerCase(Locale.ROOT);
        return switch (normalizedTarget) {
            case "equity" -> normalizedCategory.contains("equity")
                    || normalizedCategory.contains("cap")
                    || normalizedCategory.contains("index")
                    || normalizedCategory.contains("sector");
            case "debt" -> normalizedCategory.contains("debt")
                    || normalizedCategory.contains("liquid")
                    || normalizedCategory.contains("bond");
            case "gold" -> normalizedCategory.contains("gold");
            default -> normalizedCategory.contains(normalizedTarget);
        };
    }

    private boolean hasEnoughDataForSuggestion(Fund fund) {
        return isFreshFund(fund) && fund.getFundMetadataJson() != null;
    }

    private boolean isFreshFund(Fund fund) {
        if (fund.getCurrentNav() == null || fund.getLastUpdated() == null) {
            return false;
        }
        return fund.getLastUpdated().isAfter(LocalDateTime.now().minusDays(7));
    }

    private String buildRebalanceRationale(PortfolioDiagnosticDTO diagnostic,
            PortfolioDataQualityInspector.Result qualityResult) {
        List<String> points = new ArrayList<>();
        diagnostic.getSuggestions().stream()
                .limit(3)
                .forEach(suggestion -> points.add(suggestion.getMessage()));
        if (qualityResult.staleCount() > 0 || qualityResult.missingCount() > 0) {
            points.add("Some fund data is stale or incomplete, so the draft prioritizes fresher alternatives.");
        }
        return String.join(" ", points);
    }

    private String buildDraftSummary(ObjectNode draft) {
        JsonNode current = draft.path("currentAllocationSummary");
        JsonNode target = draft.path("targetAllocationSummary");
        return String.format("Current mix: Equity %.0f%% / Debt %.0f%%. Target mix: Equity %.0f%% / Debt %.0f%%.",
                current.path("Equity").asDouble(),
                current.path("Debt").asDouble(),
                target.path("Equity").asDouble(),
                target.path("Debt").asDouble());
    }

    private String expectedRiskShift(Map<String, Double> currentAllocation, Map<String, Double> targetAllocation) {
        double currentEquity = currentAllocation.getOrDefault("Equity", 0.0);
        double targetEquity = targetAllocation.getOrDefault("Equity", currentEquity);
        if (targetEquity < currentEquity) {
            return "LOWER";
        }
        if (targetEquity > currentEquity) {
            return "HIGHER";
        }
        return "STABLE";
    }

    private String expectedDiversificationImprovement(PortfolioDiagnosticDTO diagnostic) {
        long concentrationIssues = diagnostic.getSuggestions().stream()
                .filter(suggestion -> suggestion.getCategory() == PortfolioDiagnosticDTO.SuggestionCategory.SECTOR_CONCENTRATION
                        || suggestion.getCategory() == PortfolioDiagnosticDTO.SuggestionCategory.STOCK_OVERLAP
                        || suggestion.getCategory() == PortfolioDiagnosticDTO.SuggestionCategory.MARKET_CAP_IMBALANCE)
                .count();
        if (concentrationIssues >= 2) {
            return "HIGH";
        }
        if (concentrationIssues == 1) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private void emitFundTool(String toolName,
            List<Fund> funds,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter,
            boolean includePerformance,
            boolean includeRisk) {
        emit(emitter, toolStart(null, toolName));
        ArrayNode fundNodes = objectMapper.createArrayNode();

        if (funds.isEmpty()) {
            warnings.add("I could not confidently identify the fund you meant, so the answer stays generic.");
        }

        for (Fund fund : funds) {
            ObjectNode fundNode = objectMapper.createObjectNode();
            fundNode.put("fundId", fund.getFundId().toString());
            fundNode.put("fundName", fund.getFundName());
            fundNode.put("category", fund.getFundCategory());
            if (includePerformance) {
                RollingReturnsDTO rollingReturns = fundAnalyticsService.calculateRollingReturns(fund);
                fundNode.set("performance", objectMapper.valueToTree(rollingReturns));
            }
            if (includeRisk) {
                RiskInsightsDTO riskInsights = fundAnalyticsService.calculateRiskInsights(fund);
                fundNode.set("risk", objectMapper.valueToTree(riskInsights));
            }
            fundNodes.add(fundNode);
            sources.add(ChatSource.builder()
                    .label(fund.getFundName())
                    .type("FUND")
                    .entityId(fund.getFundId().toString())
                    .build());
        }

        toolTrace.set(toolName, fundNodes);
        emit(emitter, toolResult(null, toolName, fundNodes));
    }

    private List<Fund> resolveRelevantFunds(String message, List<UserHolding> holdings, int limit) {
        Map<UUID, Fund> matches = new LinkedHashMap<>();
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);

        for (UserHolding holding : holdings) {
            Fund fund = holding.getFund();
            if (fund == null || fund.getFundName() == null) {
                continue;
            }
            String fundName = fund.getFundName().toLowerCase(Locale.ROOT);
            if (normalized.contains(fundName) || fundName.contains(normalized)) {
                matches.put(fund.getFundId(), fund);
            }
        }

        if (matches.size() < limit) {
            for (String term : extractSearchTerms(message)) {
                if (term.length() < 4) {
                    continue;
                }
                List<Fund> found = fundRepository.findByFundNameContainingIgnoreCase(term);
                for (Fund fund : found) {
                    matches.putIfAbsent(fund.getFundId(), fund);
                    if (matches.size() >= limit) {
                        break;
                    }
                }
                if (matches.size() >= limit) {
                    break;
                }
            }
        }

        if (matches.isEmpty()) {
            holdings.stream()
                    .map(UserHolding::getFund)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(Fund::getFundName, Comparator.nullsLast(String::compareTo)))
                    .limit(limit)
                    .forEach(fund -> matches.putIfAbsent(fund.getFundId(), fund));
        }

        return new ArrayList<>(matches.values()).subList(0, Math.min(limit, matches.size()));
    }

    private Collection<String> extractSearchTerms(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        Set<String> results = new LinkedHashSet<>();
        for (String raw : SPLIT_PATTERN.split(message)) {
            String cleaned = raw.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9\\s-]", " ")
                    .replaceAll("\\b(fund|risk|return|returns|compare|performance|portfolio|show|me|the|my|please)\\b", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!cleaned.isBlank()) {
                results.add(cleaned);
            }
        }
        return results;
    }

    private ObjectNode buildPortfolioSnapshot(List<UserHolding> holdings) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        double totalInvestment = holdings.stream()
                .map(UserHolding::getInvestmentAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        double totalCurrent = holdings.stream()
                .map(UserHolding::getCurrentValue)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        snapshot.put("fundCount", holdings.size());
        snapshot.put("totalInvestmentAmount", roundTwoDecimals(totalInvestment));
        snapshot.put("totalCurrentValue", roundTwoDecimals(totalCurrent));
        snapshot.put("totalGainLoss", roundTwoDecimals(totalCurrent - totalInvestment));
        snapshot.put("gainLossPercentage", totalInvestment > 0
                ? roundTwoDecimals(((totalCurrent - totalInvestment) / totalInvestment) * 100.0)
                : 0.0);

        ArrayNode topHoldings = objectMapper.createArrayNode();
        holdings.stream()
                .filter(holding -> holding.getFund() != null)
                .sorted(Comparator.comparing((UserHolding holding) -> Optional.ofNullable(holding.getWeightPct()).orElse(0))
                        .reversed())
                .limit(5)
                .forEach(holding -> {
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("fundId", holding.getFund().getFundId().toString());
                    node.put("fundName", holding.getFund().getFundName());
                    node.put("weightPct", holding.getWeightPct() == null ? 0 : holding.getWeightPct());
                    node.put("currentValue", holding.getCurrentValue() == null ? 0.0 : holding.getCurrentValue());
                    topHoldings.add(node);
                });
        snapshot.set("topHoldings", topHoldings);
        return snapshot;
    }

    private ChatAction followUpAction(String label, String prompt) {
        return ChatAction.builder()
                .type("FOLLOW_UP_PROMPT")
                .label(label)
                .payload(objectNode("prompt", prompt))
                .build();
    }

    private UUID resolveConversationId(String rawConversationId) {
        if (rawConversationId == null || rawConversationId.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(rawConversationId);
        } catch (IllegalArgumentException ex) {
            return UUID.randomUUID();
        }
    }

    private void streamContent(Consumer<ChatStreamEvent> emitter, UUID conversationId, UUID assistantMessageId, String response) {
        if (emitter == null || response == null || response.isBlank()) {
            return;
        }
        for (String chunk : chunkResponse(response)) {
            emit(emitter, ChatStreamEvent.builder()
                    .type("message_delta")
                    .conversationId(conversationId.toString())
                    .assistantMessageId(assistantMessageId.toString())
                    .contentDelta(chunk)
                    .generatedAt(LocalDateTime.now())
                    .build());
        }
    }

    private List<String> chunkResponse(String response) {
        List<String> chunks = new ArrayList<>();
        String[] words = response.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (!current.isEmpty() && current.length() + word.length() + 1 > 48) {
                chunks.add(current + " ");
                current = new StringBuilder();
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private ChatStreamEvent toolStart(UUID conversationId, String toolName) {
        return event("tool_start", conversationId, null, objectNode("tool", toolName));
    }

    private ChatStreamEvent toolResult(UUID conversationId, String toolName, JsonNode payload) {
        return event("tool_result", conversationId, null, objectNode("tool", toolName, "result", payload));
    }

    private ChatStreamEvent event(String type, UUID conversationId, UUID assistantMessageId, JsonNode payload) {
        return ChatStreamEvent.builder()
                .type(type)
                .conversationId(conversationId == null ? null : conversationId.toString())
                .assistantMessageId(assistantMessageId == null ? null : assistantMessageId.toString())
                .payload(payload)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private void emit(Consumer<ChatStreamEvent> emitter, ChatStreamEvent event) {
        if (emitter != null) {
            emitter.accept(event);
        }
    }

    private ObjectNode objectNode(Object... keyValuePairs) {
        ObjectNode node = objectMapper.createObjectNode();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String key = String.valueOf(keyValuePairs[i]);
            Object value = keyValuePairs[i + 1];
            if (value == null) {
                node.putNull(key);
            } else if (value instanceof String stringValue) {
                node.put(key, stringValue);
            } else if (value instanceof Integer integerValue) {
                node.put(key, integerValue);
            } else if (value instanceof Long longValue) {
                node.put(key, longValue);
            } else if (value instanceof Double doubleValue) {
                node.put(key, doubleValue);
            } else if (value instanceof Boolean booleanValue) {
                node.put(key, booleanValue);
            } else if (value instanceof JsonNode jsonNode) {
                node.set(key, jsonNode);
            } else {
                node.set(key, objectMapper.valueToTree(value));
            }
        }
        return node;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
