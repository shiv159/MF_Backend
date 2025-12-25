package com.mutualfunds.api.mutual_fund.dto.risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DemographicsDTO {
    @NotNull(message = "Age is required")
    @Min(value = 18, message = "User must be at least 18 years old")
    private Integer age;

    @NotNull(message = "Income range is required")
    @JsonProperty("incomeRange")
    private String incomeRange;

    @NotNull(message = "Number of dependents is required")
    @Min(value = 0, message = "Dependents cannot be negative")
    private Integer dependents;
}
