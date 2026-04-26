package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

import lombok.Data;

import java.util.List;

@Data
public class RiskAssessmentResult {
    private String summary;
    private String riskShift;
    private String concentration;
    private List<String> warnings;
    private double confidence;
}
