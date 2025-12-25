package com.mutualfunds.api.mutual_fund.dto.risk;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssetAllocationDTO {
    private Double equity;
    private Double debt;
    private Double gold;
}
