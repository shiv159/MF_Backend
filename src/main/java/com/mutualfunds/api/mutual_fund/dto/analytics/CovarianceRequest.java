package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CovarianceRequest {
    private List<FundWeight> funds;

    @Data
    public static class FundWeight {
        private UUID fundId;
        private Double weight; // 0.0 to 1.0
    }

    public Map<UUID, Double> toWeightMap() {
        return funds.stream()
                .collect(java.util.stream.Collectors.toMap(
                        FundWeight::getFundId,
                        FundWeight::getWeight));
    }

    public List<UUID> getFundIds() {
        return funds.stream()
                .map(FundWeight::getFundId)
                .toList();
    }
}
