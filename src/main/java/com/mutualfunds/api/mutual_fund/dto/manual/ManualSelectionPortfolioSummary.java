package com.mutualfunds.api.mutual_fund.dto.manual;

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
