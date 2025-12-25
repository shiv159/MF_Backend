package com.mutualfunds.api.mutual_fund.dto.risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BehavioralDTO {
    @NotNull(message = "Market drop reaction is required")
    @JsonProperty("marketDropReaction")
    private String marketDropReaction; // BUY_MORE, HOLD, SELL, PANIC_SELL

    @JsonProperty("investmentPeriodExperience")
    private String investmentPeriodExperience;
}
