package com.mutualfunds.api.mutual_fund.features.ai.chat.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAction {
    private String type;
    private String label;
    private JsonNode payload;
}
