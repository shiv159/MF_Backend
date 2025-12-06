package com.mutualfunds.api.mutual_fund.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundRecommendation {
    private UUID fundId;
    private String fundName;
    private String category;
    private Double allocationPercent;
    private Double suggestedSip;
    private Double expenseRatio;
}