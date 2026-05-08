package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WorkflowUsage {
    Integer inputTokens;
    Integer outputTokens;
    Integer totalTokens;
}
