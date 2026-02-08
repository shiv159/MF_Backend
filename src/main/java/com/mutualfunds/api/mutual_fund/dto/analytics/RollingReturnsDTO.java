package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class RollingReturnsDTO {
    private String fundId;
    private String fundName;
    
    // Period Returns (as percentages, e.g., 12.5 = 12.5%)
    private Double return1M;
    private Double return3M;
    private Double return6M;
    private Double return1Y;
    private Double return3Y;
    private Double return5Y;
    
    // SIP vs Lumpsum comparison
    private Double sipReturn3Y;
    private Double lumpSumReturn3Y;
    
    // CAGR
    private Double cagr;
    
    // As of date
    private LocalDate calculatedAsOf;
}
