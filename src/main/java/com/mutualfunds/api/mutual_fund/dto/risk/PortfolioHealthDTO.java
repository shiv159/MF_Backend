package com.mutualfunds.api.mutual_fund.dto.risk;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioHealthDTO {
    private String sectorConcentration;
    private String overlapStatus;
}
