package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatAction;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatMessageRequest;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatSource;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatStreamEvent;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ChatIntent;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.IntentDecision;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowEngineType;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAgentService {

    private final IntentRouterService intentRouterService;
    private final SpringAiWorkflowEngine springAiWorkflowEngine;
    private final PortfolioToolFacade portfolioToolFacade;
    private final WorkflowEngineSelector workflowEngineSelector;
    private final AdvancedWorkflowDirector advancedWorkflowDirector;
    private final SseResponseStreamer sseResponseStreamer;
    private final ObjectMapper objectMapper;
    private final PortfolioChatPayloadFactory payloadFactory;
    private final AiWorkflowProperties properties;

    public Flux<ChatStreamEvent> streamMessage(UUID userId, ChatMessageRequest request) {
        return Flux.<ChatStreamEvent>create(sink -> Schedulers.boundedElastic().schedule(() -> {
            try {
                process(userId, request, sink::next);
                sink.complete();
            } catch (Exception ex) {
                log.error("Streaming chat failed", ex);
                sink.next(sseResponseStreamer.event("error", null, null,
                        sseResponseStreamer.objectNode("message",
                                ex.getMessage() == null ? "Unable to process request" : ex.getMessage())));
                sink.complete();
            }
        }), FluxSink.OverflowStrategy.BUFFER);
    }

    private void process(UUID userId, ChatMessageRequest request, Consumer<ChatStreamEvent> emitter) {
        long startedAt = System.currentTimeMillis();
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }

        IntentDecision decision = intentRouterService.resolveDecision(message, request.getScreenContext());
        WorkflowEngineSelector.Selection selection = workflowEngineSelector.select(decision);
        UUID conversationId = resolveConversationId(request.getConversationId());
        UUID assistantMessageId = UUID.randomUUID();

        emit(emitter, sseResponseStreamer.event("status", conversationId, null,
                sseResponseStreamer.objectNode("status",
                        request.getConversationId() == null ? "conversation_started" : "conversation_resumed")));
        emit(emitter, sseResponseStreamer.event("status", conversationId, null,
                sseResponseStreamer.objectNode("status", "intent_resolved", "intent", decision.intent().name(),
                        "route", selection.workflowRoute().name(),
                        "engine", selection.engineType().name(),
                        "confidence", decision.confidence())));

        List<UserHolding> holdings = portfolioToolFacade.findCurrentHoldings(userId);
        PortfolioDataQualityInspector.Result qualityResult = portfolioToolFacade.inspectDataQuality(holdings);

        List<String> warnings = new ArrayList<>(qualityResult.warnings());
        List<ChatSource> sources = new ArrayList<>();
        List<ChatAction> actions = new ArrayList<>();
        List<String> toolCalls = new ArrayList<>();
        ObjectNode toolTrace = objectMapper.createObjectNode();

        emitTool(emitter, conversationId, toolCalls, "portfolio_snapshot",
                portfolioToolFacade.buildPortfolioSnapshot(holdings), toolTrace, "portfolioSnapshot");
        sources.add(ChatSource.builder()
                .label("Portfolio holdings")
                .type("PORTFOLIO")
                .entityId(userId.toString())
                .build());

        boolean fallbackUsed = selection.fallbackUsed();
        String responseText;
        double responseConfidence = decision.confidence();
        String modelProfileUsed = selection.engineType() == WorkflowEngineType.LANGCHAIN4J
                ? properties.getLangchain4j().getPrimaryModelProfile()
                : "spring-ai";

        if (selection.engineType() == WorkflowEngineType.LANGCHAIN4J) {
            AgentResponse advancedResponse = executeAdvancedFlow(conversationId, request, holdings, qualityResult, warnings,
                    sources, toolCalls, emitter, selection.workflowRoute(), userId);
            responseText = advancedResponse.getSummary();
            warnings = new ArrayList<>(advancedResponse.getWarnings());
            actions.addAll(advancedResponse.getActions());
            modelProfileUsed = advancedResponse.getModelProfileUsed();
            toolCalls = new ArrayList<>(advancedResponse.getToolCalls());
            responseConfidence = advancedResponse.getConfidence();
            fallbackUsed = fallbackUsed || modelProfileUsed.toLowerCase(Locale.ROOT).contains("fallback");
        } else {
            runStandardTools(userId, decision.intent(), message, holdings, qualityResult, warnings, sources, actions, toolTrace,
                    conversationId, emitter);
            ChatSynthesisService.SynthesisResult synthesis = springAiWorkflowEngine.synthesize(
                    decision.intent(),
                    conversationId.toString(),
                    request.getScreenContext(),
                    message,
                    toolTrace,
                    warnings);
            responseText = synthesis.response();
            fallbackUsed = fallbackUsed || synthesis.fallbackUsed();
        }

        boolean requiresConfirmation = decision.requiresConfirmation()
                || actions.stream().anyMatch(action -> "REBALANCE_DRAFT".equals(action.getType()));

        for (ChatStreamEvent streamEvent : sseResponseStreamer.streamContent(conversationId, assistantMessageId, responseText)) {
            emit(emitter, streamEvent);
        }

        emit(emitter, sseResponseStreamer.event("message_complete", conversationId, assistantMessageId,
                sseResponseStreamer.objectNode(
                        "intent", decision.intent().name(),
                        "sources", objectMapper.valueToTree(sources),
                        "warnings", objectMapper.valueToTree(warnings.stream().distinct().limit(8).toList()),
                        "actions", objectMapper.valueToTree(actions),
                        "requiresConfirmation", requiresConfirmation,
                        "workflowRoute", selection.workflowRoute().name(),
                        "confidence", responseConfidence,
                        "toolCalls", objectMapper.valueToTree(toolCalls),
                        "modelProfileUsed", modelProfileUsed,
                        "fallbackUsed", fallbackUsed)));

        log.info("chat_turn intent={} route={} engine={} modelProfileUsed={} confidence={} toolCalls={} fallbackUsed={} latencyMs={}",
                decision.intent(),
                selection.workflowRoute(),
                selection.engineType(),
                modelProfileUsed,
                responseConfidence,
                toolCalls.size(),
                fallbackUsed,
                System.currentTimeMillis() - startedAt);
    }

    private AgentResponse executeAdvancedFlow(
            UUID conversationId,
            ChatMessageRequest request,
            List<UserHolding> holdings,
            PortfolioDataQualityInspector.Result qualityResult,
            List<String> warnings,
            List<ChatSource> sources,
            List<String> toolCalls,
            Consumer<ChatStreamEvent> emitter,
            WorkflowRoute route,
            UUID userId) {
        emit(emitter, sseResponseStreamer.event("status", conversationId, null,
                sseResponseStreamer.objectNode("status", "advanced_reasoning_started", "route", route.name())));

        emit(emitter, sseResponseStreamer.toolStart(conversationId, "advanced_agent_context"));
        Optional<RiskProfileResponse> riskProfile = portfolioToolFacade.findRiskProfile(userId);
        PortfolioDiagnosticDTO diagnostic = portfolioToolFacade.runDiagnostic(userId);
        AgentContextBundle contextBundle = portfolioToolFacade.buildAgentContextBundle(
                userId,
                request.getMessage(),
                request.getScreenContext(),
                holdings,
                qualityResult,
                riskProfile,
                diagnostic);
        toolCalls.add("advanced_agent_context");
        emit(emitter, sseResponseStreamer.toolResult(conversationId, "advanced_agent_context",
                portfolioToolFacade.toToolTrace(contextBundle)));

        sources.add(ChatSource.builder().label("Portfolio diagnostic").type("DIAGNOSTIC")
                .entityId(contextBundle.getUserId().toString()).build());
        sources.add(ChatSource.builder().label("Risk profile").type("RISK_PROFILE")
                .entityId(contextBundle.getUserId().toString()).build());
        sources.add(ChatSource.builder().label("Stored fund analytics").type("FUND_ANALYTICS")
                .entityId(contextBundle.getUserId().toString()).build());

        try {
            UUID contextUserId = contextBundle.getUserId();
            return CompletableFuture.supplyAsync(() -> {
                        if (contextUserId != null) {
                            ToolExecutionContextHolder.setUserId(contextUserId);
                        } else {
                            ToolExecutionContextHolder.clear();
                        }
                        try {
                            return advancedWorkflowDirector.execute(route, contextBundle, conversationId);
                        } finally {
                            ToolExecutionContextHolder.clear();
                        }
                    })
                    .orTimeout(properties.getTotalAdvancedFlowTimeoutMs(), TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("Advanced workflow timed out or failed: {}", ex.getMessage());
                        return AgentResponse.builder()
                                .summary("I reviewed the scenario with your saved portfolio data, but the advanced reasoning path timed out so this answer is conservative and advisory.")
                                .warnings(mergeWarnings(contextBundle.getWarnings(),
                                        List.of("Advanced reasoning timed out, so the response was simplified.")))
                                .actions(defaultAdvancedFallbackActions(route))
                                .confidence(0.46)
                                .workflowRoute(WorkflowRoute.SPRING_FALLBACK_CHAT)
                                .toolCalls(List.of("advanced_agent_context"))
                                .modelProfileUsed("spring-fallback")
                                .requiresConfirmation(route == WorkflowRoute.LC4J_REBALANCE_CRITIQUE)
                                .build();
                    })
                    .join();
        } finally {
            warnings.addAll(contextBundle.getWarnings());
        }
    }

    private void runStandardTools(
            UUID userId,
            ChatIntent intent,
            String message,
            List<UserHolding> holdings,
            PortfolioDataQualityInspector.Result qualityResult,
            List<String> warnings,
            List<ChatSource> sources,
            List<ChatAction> actions,
            ObjectNode toolTrace,
            UUID conversationId,
            Consumer<ChatStreamEvent> emitter) {
        switch (intent) {
            case DATA_QUALITY -> handleDataQuality(conversationId, qualityResult, toolTrace, sources, emitter);
            case FUND_COMPARE -> handleFundCompare(message, holdings, toolTrace, sources, warnings, conversationId, emitter);
            case FUND_RISK -> handleFundRisk(message, holdings, toolTrace, sources, warnings, conversationId, emitter);
            case FUND_PERFORMANCE -> handleFundPerformance(message, holdings, toolTrace, sources, warnings, conversationId, emitter);
            case RISK_PROFILE_EXPLAINER -> handleRiskProfile(userId, conversationId, toolTrace, sources, warnings, emitter);
            case DIAGNOSTIC_EXPLAINER -> handleDiagnostic(userId, conversationId, toolTrace, sources, warnings, emitter);
            case PORTFOLIO_SUMMARY -> handlePortfolioSummary(userId, conversationId, toolTrace, sources, warnings, emitter);
            case GENERAL_QA -> handleGeneralQuestion(userId, conversationId, toolTrace, sources, warnings, emitter);
            case REBALANCE_DRAFT -> handleRebalanceDraft(userId, conversationId, holdings, qualityResult, toolTrace, sources, warnings,
                    actions, emitter);
            case SCENARIO_ANALYSIS -> handleScenarioFallback(userId, conversationId, holdings, qualityResult, toolTrace, sources, warnings,
                    actions, emitter);
        }
    }

    private void handleDataQuality(UUID conversationId,
            PortfolioDataQualityInspector.Result qualityResult,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            Consumer<ChatStreamEvent> emitter) {
        ObjectNode qualityNode = payloadFactory.buildDataQualityPayload(qualityResult);
        emitTool(emitter, conversationId, null, "data_quality", qualityNode, toolTrace, "dataQuality");
        sources.add(ChatSource.builder().label("Data quality checks").type("DATA_QUALITY").entityId(null).build());
    }

    private void handleFundCompare(String message,
            List<UserHolding> holdings,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            UUID conversationId,
            Consumer<ChatStreamEvent> emitter) {
        List<Fund> funds = portfolioToolFacade.resolveRelevantFunds(message, holdings, 2);
        emitFundTool("fund_compare", funds, toolTrace, sources, warnings, emitter, true, true, conversationId);
    }

    private void handleFundRisk(String message,
            List<UserHolding> holdings,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            UUID conversationId,
            Consumer<ChatStreamEvent> emitter) {
        List<Fund> funds = portfolioToolFacade.resolveRelevantFunds(message, holdings, 2);
        emitFundTool("fund_risk", funds, toolTrace, sources, warnings, emitter, false, true, conversationId);
    }

    private void handleFundPerformance(String message,
            List<UserHolding> holdings,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            UUID conversationId,
            Consumer<ChatStreamEvent> emitter) {
        List<Fund> funds = portfolioToolFacade.resolveRelevantFunds(message, holdings, 2);
        emitFundTool("fund_performance", funds, toolTrace, sources, warnings, emitter, true, false, conversationId);
    }

    private void handleRiskProfile(UUID userId,
            UUID conversationId,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        emit(emitter, sseResponseStreamer.toolStart(conversationId, "risk_profile"));
        Optional<RiskProfileResponse> profile = portfolioToolFacade.findRiskProfile(userId);
        if (profile.isEmpty()) {
            warnings.add("Risk profile is incomplete, so the allocation guidance may be limited.");
            toolTrace.set("riskProfile", sseResponseStreamer.objectNode("status", "missing_profile"));
            emit(emitter, sseResponseStreamer.toolResult(conversationId, "risk_profile", toolTrace.path("riskProfile")));
            return;
        }

        toolTrace.set("riskProfile", objectMapper.valueToTree(profile.get()));
        sources.add(ChatSource.builder()
                .label("Risk profile")
                .type("RISK_PROFILE")
                .entityId(userId.toString())
                .build());
        emit(emitter, sseResponseStreamer.toolResult(conversationId, "risk_profile", toolTrace.path("riskProfile")));
    }

    private void handleDiagnostic(UUID userId,
            UUID conversationId,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        PortfolioDiagnosticDTO diagnostic = portfolioToolFacade.runDiagnostic(userId);
        emitTool(emitter, conversationId, null, "portfolio_diagnostic", objectMapper.valueToTree(diagnostic), toolTrace, "diagnostic");
        long highSeverityCount = diagnostic.getSuggestions() == null ? 0 : diagnostic.getSuggestions().stream()
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
    }

    private void handlePortfolioSummary(UUID userId,
            UUID conversationId,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        handleDiagnostic(userId, conversationId, toolTrace, sources, warnings, emitter);
    }

    private void handleGeneralQuestion(UUID userId,
            UUID conversationId,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter) {
        handleDiagnostic(userId, conversationId, toolTrace, sources, warnings, emitter);
        if (!toolTrace.has("riskProfile")) {
            handleRiskProfile(userId, conversationId, toolTrace, sources, warnings, emitter);
        }
    }

    private void handleScenarioFallback(UUID userId,
            UUID conversationId,
            List<UserHolding> holdings,
            PortfolioDataQualityInspector.Result qualityResult,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            List<ChatAction> actions,
            Consumer<ChatStreamEvent> emitter) {
        handleDiagnostic(userId, conversationId, toolTrace, sources, warnings, emitter);
        handleRiskProfile(userId, conversationId, toolTrace, sources, warnings, emitter);
        ObjectNode scenario = payloadFactory.buildScenarioFallbackPayload(holdings, qualityResult);
        emitTool(emitter, conversationId, null, "scenario_analysis", scenario, toolTrace, "scenarioAnalysis");
        actions.add(followUpAction("Show a more conservative version", "Show a more conservative version"));
        actions.add(followUpAction("Why did you suggest this?", "Why did you suggest this?"));
        warnings.add("Advanced scenario reasoning was unavailable, so this scenario stayed on the simpler fallback path.");
    }

    private void handleRebalanceDraft(UUID userId,
            UUID conversationId,
            List<UserHolding> holdings,
            PortfolioDataQualityInspector.Result qualityResult,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            List<ChatAction> actions,
            Consumer<ChatStreamEvent> emitter) {
        emit(emitter, sseResponseStreamer.toolStart(conversationId, "rebalance_draft"));

        PortfolioDiagnosticDTO diagnostic = portfolioToolFacade.runDiagnostic(userId);
        toolTrace.set("diagnostic", objectMapper.valueToTree(diagnostic));

        RiskProfileResponse riskProfile = null;
        Optional<RiskProfileResponse> profile = portfolioToolFacade.findRiskProfile(userId);
        if (profile.isPresent()) {
            riskProfile = profile.get();
            toolTrace.set("riskProfile", objectMapper.valueToTree(riskProfile));
        } else {
            warnings.add("Risk profile data is incomplete, so this rebalance draft stays at category level.");
        }

        ObjectNode draft = payloadFactory.buildRebalanceDraft(holdings, diagnostic, riskProfile, qualityResult, warnings);
        toolTrace.set("rebalanceDraft", draft);
        toolTrace.put("draftSummary", payloadFactory.buildDraftSummary(draft));

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
        emit(emitter, sseResponseStreamer.toolResult(conversationId, "rebalance_draft", draft));
    }

    private void emitFundTool(String toolName,
            List<Fund> funds,
            ObjectNode toolTrace,
            List<ChatSource> sources,
            List<String> warnings,
            Consumer<ChatStreamEvent> emitter,
            boolean includePerformance,
            boolean includeRisk,
            UUID conversationId) {
        emit(emitter, sseResponseStreamer.toolStart(conversationId, toolName));
        JsonNode fundNodes = payloadFactory.buildFundPayload(funds, includePerformance, includeRisk);

        if (funds.isEmpty()) {
            warnings.add("I could not confidently identify the fund you meant, so the answer stays generic.");
        }

        for (Fund fund : funds) {
            sources.add(ChatSource.builder()
                    .label(fund.getFundName())
                    .type("FUND")
                    .entityId(fund.getFundId().toString())
                    .build());
        }

        toolTrace.set(toolName, fundNodes);
        emit(emitter, sseResponseStreamer.toolResult(conversationId, toolName, fundNodes));
    }

    private List<ChatAction> defaultAdvancedFallbackActions(WorkflowRoute route) {
        return switch (route) {
            case LC4J_SCENARIO_ANALYSIS -> List.of(
                    followUpAction("Show a more conservative version", "Show a more conservative version"),
                    followUpAction("Why did you suggest this?", "Why did you suggest this?"));
            case LC4J_REBALANCE_CRITIQUE -> List.of(
                    followUpAction("Explain the biggest trade-off", "Explain the biggest trade-off"),
                    followUpAction("Show a simpler rebalance", "Show a simpler rebalance"));
            case LC4J_RECOMMENDATION_SYNTHESIS -> List.of(
                    followUpAction("Compare the downside risk", "Compare the downside risk"),
                    followUpAction("Why does this fit my profile?", "Why does this fit my profile?"));
            default -> List.of();
        };
    }

    private List<String> mergeWarnings(Collection<String> left, Collection<String> right) {
        Set<String> warnings = new LinkedHashSet<>();
        if (left != null) {
            warnings.addAll(left);
        }
        if (right != null) {
            warnings.addAll(right);
        }
        return warnings.stream().limit(8).toList();
    }

    private ChatAction followUpAction(String label, String prompt) {
        return ChatAction.builder()
                .type("FOLLOW_UP_PROMPT")
                .label(label)
                .payload(sseResponseStreamer.objectNode("prompt", prompt))
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

    private void emitTool(Consumer<ChatStreamEvent> emitter,
            UUID conversationId,
            List<String> toolCalls,
            String toolName,
            JsonNode payload,
            ObjectNode toolTrace,
            String traceKey) {
        if (toolCalls != null) {
            if (properties.isDuplicateToolSuppression() && toolCalls.contains(toolName)) {
                return;
            }
            if (toolCalls.size() >= properties.getMaxToolCallsPerTurn()) {
                return;
            }
            toolCalls.add(toolName);
        }
        emit(emitter, sseResponseStreamer.toolStart(conversationId, toolName));
        toolTrace.set(traceKey, payload);
        emit(emitter, sseResponseStreamer.toolResult(conversationId, toolName, payload));
    }

    private void emit(Consumer<ChatStreamEvent> emitter, ChatStreamEvent event) {
        if (emitter != null) {
            emitter.accept(event);
        }
    }

}
