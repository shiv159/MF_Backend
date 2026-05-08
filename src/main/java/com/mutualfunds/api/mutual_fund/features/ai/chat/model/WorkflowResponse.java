package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class WorkflowResponse<T> {
    T content;
    String rawContent;
    String modelProfileUsed;
    boolean fallbackUsed;
    WorkflowUsage usage;
    @Builder.Default
    List<String> executedTools = List.of();
    long latencyMs;
}
