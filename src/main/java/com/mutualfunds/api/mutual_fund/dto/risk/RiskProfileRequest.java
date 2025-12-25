package com.mutualfunds.api.mutual_fund.dto.risk;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RiskProfileRequest {
    @Valid
    @NotNull(message = "Demographics are required")
    private DemographicsDTO demographics;

    @Valid
    @NotNull(message = "Financial details are required")
    private FinancialsDTO financials;

    @Valid
    @NotNull(message = "Behavioral details are required")
    private BehavioralDTO behavioral;

    @Valid
    @NotNull(message = "Goals are required")
    private GoalDTO goals;

    private PreferencesDTO preferences;
}
