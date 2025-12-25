package com.mutualfunds.api.mutual_fund.dto.risk;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RecommendationCategoryDTO {
    private String allocationCategory;
    private Double allocationPercent;
    private Double amount;
    private List<FundRecommendationDTO> funds;
}
