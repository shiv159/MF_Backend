package com.mutualfunds.api.mutual_fund.dto.risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PreferencesDTO {
    @JsonProperty("preferredInvestmentStyle")
    private String preferredInvestmentStyle; // ACTIVE, PASSIVE, HYBRID

    @JsonProperty("taxSavingNeeded")
    private Boolean taxSavingNeeded;
}
