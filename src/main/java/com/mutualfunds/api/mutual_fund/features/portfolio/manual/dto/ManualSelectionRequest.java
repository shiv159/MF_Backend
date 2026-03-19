package com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualSelectionRequest {

    @NotEmpty(message = "selections cannot be empty")
    @Valid
    private List<ManualSelectionItemRequest> selections;
}
