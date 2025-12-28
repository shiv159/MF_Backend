package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class YearProjection {
    private int year;
    private double optimisticAmount;
    private double expectedAmount;
    private double pessimisticAmount;
}
