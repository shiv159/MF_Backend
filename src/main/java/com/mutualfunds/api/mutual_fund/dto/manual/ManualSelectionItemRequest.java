package com.mutualfunds.api.mutual_fund.dto.manual;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualSelectionItemRequest {

    private UUID fundId;

    private String fundName;

    @NotNull(message = "weightPct is required")
    @Min(value = 1, message = "weightPct must be between 1 and 100")
    @Max(value = 100, message = "weightPct must be between 1 and 100")
    private Integer weightPct;

    @AssertTrue(message = "Exactly one of fundId or fundName must be provided")
    public boolean isValidRef() {
        boolean hasFundId = fundId != null;
        boolean hasFundName = fundName != null && !fundName.trim().isEmpty();
        return hasFundId ^ hasFundName;
    }
}
