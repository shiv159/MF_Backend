package com.mutualfunds.api.mutual_fund.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Enriched fund data returned by Python ETL service
 * Contains all fund master data needed for persistence
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichedFund {
    // From parsed holding
    private String fundName;
    private BigDecimal units;           // User's units
    private BigDecimal nav;             // User's NAV
    private BigDecimal value;           // User's value
    
    // From mftool enrichment
    private String isin;
    private String amc;                 // AMC name
    private String category;            // Large-Cap, Mid-Cap, etc.
    private Double expenseRatio;        // 0.75
    private Double currentNav;          // Latest NAV
    private LocalDate navAsOf;          // NAV date
    
    // JSON columns for analytics
    private JsonNode sectorAllocation;  // {"Finance": 28, "IT": 18, ...}
    private JsonNode topHoldings;       // [{"symbol": "HDFC", "company": "HDFC Bank", "weight": 8.5}, ...]
}
