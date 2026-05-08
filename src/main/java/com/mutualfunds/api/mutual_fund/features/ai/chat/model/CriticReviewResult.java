package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

import lombok.Data;

import java.util.List;

@Data
public class CriticReviewResult {
    private boolean approved;
    private String summaryAdjustment;
    private List<String> warnings;
    private double confidenceAdjustment;
}
