package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentResponse;
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

    public AgentResponse execute(WorkflowRoute route, AgentContextBundle context) {
        long startedAt = System.currentTimeMillis();
        CompletableFuture<com.fasterxml.jackson.databind.JsonNode> riskFuture =
                CompletableFuture.supplyAsync(() -> riskAssessorAgent.analyze(context));
        CompletableFuture<com.fasterxml.jackson.databind.JsonNode> marketFuture =
                CompletableFuture.supplyAsync(() -> marketAnalystAgent.analyze(context));

        var riskAssessment = riskFuture.join();
        var marketAssessment = marketFuture.join();
        AgentResponse draft = financialAdvisorAgent.advise(context, route, riskAssessment, marketAssessment);
        AgentResponse reviewed = criticAgent.review(context, draft);
        log.info("advanced_workflow route={} modelProfileUsed={} latencyMs={}",
                route,
                reviewed.getModelProfileUsed(),
                System.currentTimeMillis() - startedAt);
        return reviewed;
    }
}
