package com.mutualfunds.api.mutual_fund.ai.chat.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {
    private String type;
    private String conversationId;
    private String assistantMessageId;
    private String contentDelta;
    private JsonNode payload;
    private LocalDateTime generatedAt;
}
