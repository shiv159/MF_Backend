package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

public record IntentDecision(
        ChatIntent intent,
        String toolGroup,
        WorkflowRoute route,
        double confidence,
        boolean requiresConfirmation) {
}
