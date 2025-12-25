package com.mutualfunds.api.mutual_fund.dto.risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class GoalDTO {
    @NotNull(message = "Primary goal is required")
    @JsonProperty("primaryGoal")
    private String primaryGoal;

    @NotNull(message = "Time horizon is required")
    @Positive(message = "Time horizon must be positive")
    @JsonProperty("timeHorizonYears")
    private Integer timeHorizonYears;

    @Positive(message = "Target amount must be positive")
    @JsonProperty("targetAmount")
    private Double targetAmount;
}
