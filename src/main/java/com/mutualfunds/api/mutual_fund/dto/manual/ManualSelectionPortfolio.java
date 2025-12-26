package com.mutualfunds.api.mutual_fund.dto.manual;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualSelectionPortfolio {

    private ManualSelectionPortfolioSummary summary;

    private List<ManualSelectionHolding> holdings;
}
