package com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualSelectionPortfolioSummary {

    private Integer totalHoldings;
    private Integer totalWeightPct;
}
