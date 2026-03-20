package com.mutualfunds.api.mutual_fund.features.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSource {
    private String label;
    private String type;
    private String entityId;
}
