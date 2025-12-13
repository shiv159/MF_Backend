package com.mutualfunds.api.mutual_fund.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

/**
 * DTO for a single holding entry sent to ETL enrichment service.
 * Maps to Python ETL service's ParsedHoldingEntry Pydantic model.
 * 
 * Represents a mutual fund holding extracted from broker portfolio statement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedHoldingEntry {
    
    @NotBlank(message = "Fund name is required")
    @JsonProperty("fund_name")
    private String fundName;
    
    @NotNull(message = "Units is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Units must be greater than 0")
    @JsonProperty("units")
    private Double units;
    
    @JsonProperty("nav")
    private Double nav;  // Net Asset Value (if available from enrichment)
    
    @JsonProperty("value")
    private Double value;  // Total holding value (units * nav), optional
    
    @JsonProperty("purchase_date")
    private String purchaseDate;  // ISO format if available
    
    /**
     * Additional fields from Excel not in basic ETL schema
     * (kept for reference but not sent to basic ETL)
     */
    @JsonProperty("isin")
    private String isin;
    
    @JsonProperty("amc")
    private String amc;
    
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("folio_number")
    private String folioNumber;
    
    @JsonProperty("current_value")
    private Double currentValue;
    
    @JsonProperty("returns")
    private Double returns;
    
    @JsonProperty("xirr")
    private Double xirr;
}
