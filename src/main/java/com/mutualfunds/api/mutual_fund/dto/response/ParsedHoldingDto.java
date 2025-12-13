package com.mutualfunds.api.mutual_fund.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

/**
 * DTO for holdings parsed from Excel files.
 * Maps to broker portfolio statement columns with full column support.
 *
 * Excel Column Mapping:
 * - Scheme Name → fundName
 * - ISIN → isin
 * - AMC → amc
 * - Category → category
 * - Sub-category → subCategory
 * - Folio No. → folioNumber
 * - Source → source
 * - Units → units
 * - Invested Value → investedValue
 * - Current Value → currentValue
 * - Returns → returns (percentage)
 * - XIRR → xirr (percentage)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedHoldingDto {
    
    @NotBlank(message = "Fund name is required")
    private String fundName;
    
    @Pattern(regexp = "^[A-Z0-9]{12}$|^$", message = "ISIN must be 12 alphanumeric characters or empty")
    private String isin;
    
    private String amc;  // Asset Management Company
    
    private String category;  // E.g., "Large Cap", "Mid Cap", "Hybrid"
    
    private String subCategory;  // E.g., "Growth", "Income"
    
    @NotNull(message = "Folio number is required")
    private String folioNumber;
    
    @Builder.Default
    private String source = "excel";  // Source of data ("excel", "pdf", etc.)
    
    @NotNull(message = "Units is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Units must be greater than 0")
    private Double units;
    
    @DecimalMin(value = "0.0", inclusive = true, message = "Invested value cannot be negative")
    private Double investedValue;  // Amount invested
    
    @NotNull(message = "Current value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Current value must be greater than 0")
    private Double currentValue;  // Current market value
    
    @DecimalMin(value = "-100.0", message = "Returns cannot be less than -100%")
    private Double returns;  // Percentage returns
    
    @DecimalMin(value = "-100.0", message = "XIRR cannot be less than -100%")
    private Double xirr;  // Annualized return percentage
    
    // Metadata fields
    @Builder.Default
    private String parseStatus = "SUCCESS";  // SUCCESS or FAILED
    
    private String validationError;  // Error message if parsing/validation failed
}
