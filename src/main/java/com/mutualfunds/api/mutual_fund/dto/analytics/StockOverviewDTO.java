package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class StockOverviewDTO {
    private String stockName;
    private String isin;
    private Double totalWeight;
    private Integer fundCount;
    private List<String> fundNames;
}
