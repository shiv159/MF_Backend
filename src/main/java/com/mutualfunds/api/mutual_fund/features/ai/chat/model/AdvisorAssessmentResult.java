package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

import lombok.Data;

import java.util.List;

@Data
public class AdvisorAssessmentResult {
    private String summary;
    private List<String> warnings;
    private double confidence;
}
