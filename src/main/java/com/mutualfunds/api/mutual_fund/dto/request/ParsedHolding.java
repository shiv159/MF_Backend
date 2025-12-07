package com.mutualfunds.api.mutual_fund.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a holding extracted from PDF/Excel upload (pre-enrichment)
 * Spring Boot extracts this, Python ETL enriches it
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedHolding {
    private String fundName;           // e.g., "HDFC Mid-Cap Growth"
    private BigDecimal units;          // e.g., 150.45
    private BigDecimal nav;            // e.g., 1485.50
    private BigDecimal value;          // e.g., 223495.48 (units * nav)
    private LocalDate purchaseDate;    // e.g., 2020-06-15
}
