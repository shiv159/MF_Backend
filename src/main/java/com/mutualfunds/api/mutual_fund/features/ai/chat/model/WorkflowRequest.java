package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
@Builder(toBuilder = true)
public class WorkflowRequest<T> {
    UUID conversationId;
    UUID executionUserId;
    String scope;
    WorkflowRoute route;
    ToolDetailLevel detailLevel;
    String userQuestion;
    @Builder.Default
    Map<String, Object> seedContext = Map.of();
    String systemPrompt;
    String outputSchemaHint;
    Class<T> outputType;
    @Singular
    List<ChatMessage> messages;
    @Singular("selectedTool")
    List<String> selectedTools;
}
