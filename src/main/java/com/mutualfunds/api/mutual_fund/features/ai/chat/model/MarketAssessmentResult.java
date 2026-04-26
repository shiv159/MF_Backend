package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

import lombok.Data;

import java.util.List;

@Data
public class MarketAssessmentResult {
    private String benchmarkContext;
    private String categoryContext;
    private List<String> marketEvidence;
    private List<String> marketWarnings;
    private String freshnessStatus;
    private double confidence;
}
