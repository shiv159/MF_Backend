package com.mutualfunds.api.mutual_fund.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskProfileResponse {
    private String nextStep;
    private StarterPlanDTO starterPlan;
}