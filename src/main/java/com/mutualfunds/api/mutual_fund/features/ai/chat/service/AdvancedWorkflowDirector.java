package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.MarketAssessmentResult;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.RiskAssessmentResult;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedWorkflowDirector {

    private final RiskAssessorAgent riskAssessorAgent;
    private final MarketAnalystAgent marketAnalystAgent;
    private final FinancialAdvisorAgent financialAdvisorAgent;
    private final CriticAgent criticAgent;

    public AgentResponse execute(WorkflowRoute route, AgentContextBundle context, java.util.UUID conversationId) {
        long startedAt = System.currentTimeMillis();
        java.util.UUID capturedUserId = context.getUserId();

        CompletableFuture<RiskAssessmentResult> riskFuture =
                CompletableFuture.supplyAsync(() ->
                        runWithUserContext(capturedUserId, () -> riskAssessorAgent.analyze(route, context, conversationId)));

        CompletableFuture<MarketAssessmentResult> marketFuture =
                CompletableFuture.supplyAsync(() ->
                        runWithUserContext(capturedUserId, () -> marketAnalystAgent.analyze(route, context, conversationId)));

        var riskAssessment = riskFuture.join();
        var marketAssessment = marketFuture.join();

        AgentResponse draft = runWithUserContext(capturedUserId,
                () -> financialAdvisorAgent.advise(context, route, riskAssessment, marketAssessment, conversationId));

        AgentResponse reviewed = runWithUserContext(capturedUserId,
                () -> criticAgent.review(context, draft, conversationId));

        log.info("advanced_workflow route={} modelProfileUsed={} latencyMs={}",
                route,
                reviewed.getModelProfileUsed(),
                System.currentTimeMillis() - startedAt);
        return reviewed;
    }

    private <T> T runWithUserContext(java.util.UUID userId, java.util.function.Supplier<T> work) {
        if (userId != null) {
            ToolExecutionContextHolder.setUserId(userId);
        } else {
            ToolExecutionContextHolder.clear();
        }
        try {
            return work.get();
        } finally {
            ToolExecutionContextHolder.clear();
        }
    }
}
