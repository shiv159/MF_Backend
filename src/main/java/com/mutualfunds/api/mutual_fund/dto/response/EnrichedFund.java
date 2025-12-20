package com.mutualfunds.api.mutual_fund.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * Uses @JsonProperty and @JsonAlias for snake_case JSON deserialization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class EnrichedFund {
    // From parsed holding
    @JsonProperty("fund_name")
    @JsonAlias("fundName")
    private String fundName;

    @JsonProperty("units")
    @JsonAlias("units")
    private BigDecimal units; // User's units

    @JsonProperty("nav")
    @JsonAlias("nav")
    private BigDecimal nav; // User's NAV

    @JsonProperty("value")
    @JsonAlias("value")
    private BigDecimal value; // User's value

    // From mftool enrichment
    @JsonProperty("isin")
    @JsonAlias("isin")
    private String isin;

    @JsonProperty("amc")
    @JsonAlias("amc")
    private String amc; // AMC name

    @JsonProperty("category")
    @JsonAlias("category")
    private String category; // Large-Cap, Mid-Cap, etc.

    @JsonProperty("expense_ratio")
    @JsonAlias("expenseRatio")
    private Double expenseRatio; // 0.75

    @JsonProperty("current_nav")
    @JsonAlias("currentNav")
    private Double currentNav; // Latest NAV

    @JsonProperty("nav_as_of")
    @JsonAlias("navAsOf")
    private String navAsOf; // NAV date (as returned by ETL; formats may vary, e.g. dd-MM-yyyy)

    // JSON columns for analytics
    @JsonProperty("sector_allocation")
    @JsonAlias("sectorAllocation")
    private JsonNode sectorAllocation; // {"Finance": 28, "IT": 18, ...}

    @JsonProperty("top_holdings")
    @JsonAlias("topHoldings")
    private JsonNode topHoldings; // [{"symbol": "HDFC", "company": "HDFC Bank", "weight": 8.5}, ...]

    @JsonProperty("fund_metadata")
    @JsonAlias({ "fundMetadata", "mstarpyMetadata", "mstarpy_metadata" })
    private JsonNode fundMetadata; // Fund metadata (Morningstar and other enrichment data)
}
