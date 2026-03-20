package com.mutualfunds.api.mutual_fund.features.portfolio.manual.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import com.mutualfunds.api.mutual_fund.features.risk.dto.PortfolioHealthDTO;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualSelectionResponse {

    private List<ManualSelectionResult> results;

    private ManualSelectionPortfolio portfolio;
    private PortfolioHealthDTO analysis;
}
