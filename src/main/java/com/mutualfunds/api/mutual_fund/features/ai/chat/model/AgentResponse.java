package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatAction;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AgentResponse {
    String summary;
    @Builder.Default
    List<String> warnings = List.of();
    @Builder.Default
    List<ChatAction> actions = List.of();
    double confidence;
    @Builder.Default
    List<String> toolCalls = List.of();
    String modelProfileUsed;
    WorkflowRoute workflowRoute;
    boolean requiresConfirmation;
}
