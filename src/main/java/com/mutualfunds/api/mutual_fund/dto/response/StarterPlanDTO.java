package com.mutualfunds.api.mutual_fund.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StarterPlanDTO {
    private Map<String, Double> equityDebtSplit;
    private List<FundRecommendation> recommendations;
    private Double totalMonthlySip;
    private String rationale;
}