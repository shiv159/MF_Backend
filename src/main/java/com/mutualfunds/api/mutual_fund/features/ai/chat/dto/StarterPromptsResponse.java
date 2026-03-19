package com.mutualfunds.api.mutual_fund.features.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StarterPromptsResponse {
    @Builder.Default
    private List<String> prompts = new ArrayList<>();
}
