package com.mutualfunds.api.mutual_fund.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mutualfunds.api.mutual_fund.enums.RiskTolerance;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RiskProfileRequest {
    @NotNull
    @Positive
    @JsonProperty("investmentHorizon")
    private Integer horizon;

    @NotNull
    @JsonProperty("riskTolerance")
    private RiskTolerance risk;

    @NotNull
    @Positive
    @JsonProperty("monthlyInvestment")
    private Double sip;

    @NotNull
    @JsonProperty("primaryGoal")
    private String goal;
}