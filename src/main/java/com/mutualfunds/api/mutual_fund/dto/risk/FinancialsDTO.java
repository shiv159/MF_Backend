package com.mutualfunds.api.mutual_fund.dto.risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FinancialsDTO {
    @NotNull(message = "Emergency fund details are required")
    @Min(value = 0)
    @JsonProperty("emergencyFundMonths")
    private Integer emergencyFundMonths;

    @Min(value = 0)
    @JsonProperty("existingEmiForLoans")
    private Double existingEmiForLoans;

    @JsonProperty("financialKnowledge")
    private String financialKnowledge; // BEGINNER, INTERMEDIATE, ADVANCED

    @Min(value = 500)
    @JsonProperty("monthlyInvestmentAmount")
    private Double monthlyInvestmentAmount;
}
