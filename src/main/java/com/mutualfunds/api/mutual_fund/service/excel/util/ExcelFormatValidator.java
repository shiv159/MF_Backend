package com.mutualfunds.api.mutual_fund.service.excel.util;

import com.mutualfunds.api.mutual_fund.dto.response.ParsedHoldingDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Validates Excel file structure and extracted data quality
 */
@Component
@Slf4j
public class ExcelFormatValidator {
    
    private static final int MINIMUM_REQUIRED_HEADERS = 3; // At least fund name, isin, units or value
    private static final int MINIMUM_REQUIRED_ROWS = 1;
    
    /**
     * Validate if extracted table has required structure
     * 
     * @param detectedHeaders Headers detected from table
     * @param rawRows Rows extracted from table
     * @param columnAliasRegistry Registry for header validation
     * @return true if table appears valid
     */
    public boolean validateTableStructure(
            List<String> detectedHeaders,
            List<Map<String, String>> rawRows,
            ColumnAliasRegistry columnAliasRegistry) {
        
        if (detectedHeaders == null || detectedHeaders.isEmpty()) {
            log.warn("No headers detected in table");
            return false;
        }
        
        if (rawRows == null || rawRows.size() < MINIMUM_REQUIRED_ROWS) {
            log.warn("Insufficient rows: {}", rawRows == null ? 0 : rawRows.size());
            return false;
        }
        
        // Check if at least 3 standard columns are detected
        int matchedStandardColumns = 0;
        for (String header : detectedHeaders) {
            if (columnAliasRegistry.isKnownAlias(header)) {
                matchedStandardColumns++;
            }
        }
        
        if (matchedStandardColumns < MINIMUM_REQUIRED_HEADERS) {
            log.warn("Too few standard columns detected: {}. Required at least {}",
                    matchedStandardColumns, MINIMUM_REQUIRED_HEADERS);
            return false;
        }
        
        log.debug("Table validation passed. Headers: {}, Rows: {}, Matched columns: {}",
                detectedHeaders.size(), rawRows.size(), matchedStandardColumns);
        return true;
    }
    
    /**
     * Validate a single row has required fields
     * ISIN is optional (some broker statements don't include it)
     * Requires: fundName AND (units OR currentValue)
     * 
     * @param row Row to validate
     * @return true if row has fund name and at least units or current value
     */
    public boolean validateRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        
        // Fund name is required
        Object fundNameObj = row.get("fundName");
        String fundName = fundNameObj != null ? fundNameObj.toString().trim() : "";
        
        if (fundName.isEmpty()) {
            return false;
        }
        
        // At least one of units or currentValue must be present and non-zero
        Object unitsObj = row.get("units");
        Object currentValueObj = row.get("currentValue");
        
        double units = 0.0;
        double currentValue = 0.0;
        
        if (unitsObj instanceof Number) {
            units = ((Number) unitsObj).doubleValue();
        }
        
        if (currentValueObj instanceof Number) {
            currentValue = ((Number) currentValueObj).doubleValue();
        }
        
        // Valid if we have fund name AND (units > 0 OR currentValue > 0)
        boolean hasValidUnits = units > 0;
        boolean hasValidValue = currentValue > 0;
        
        return hasValidUnits || hasValidValue;
    }
    
    /**
     * Validate a ParsedHoldingDto
     * Requires: fundName AND folioNumber AND (units OR currentValue)
     * 
     * @param holding Holding DTO to validate
     * @return true if holding has required fields with valid values
     */
    public boolean validateHolding(ParsedHoldingDto holding) {
        if (holding == null) {
            return false;
        }
        
        // Fund name is required
        if (holding.getFundName() == null || holding.getFundName().trim().isEmpty()) {
            log.debug("Holding validation failed: missing fundName");
            return false;
        }
        
        // Folio number is required
        if (holding.getFolioNumber() == null || holding.getFolioNumber().trim().isEmpty()) {
            log.debug("Holding validation failed: missing folioNumber");
            return false;
        }
        
        // At least one of units or currentValue must be present and non-zero
        double units = holding.getUnits() != null ? holding.getUnits() : 0.0;
        double currentValue = holding.getCurrentValue() != null ? holding.getCurrentValue() : 0.0;
        
        boolean hasValidUnits = units > 0;
        boolean hasValidValue = currentValue > 0;
        
        if (!(hasValidUnits || hasValidValue)) {
            log.debug("Holding validation failed: units={}, currentValue={}", units, currentValue);
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate ISIN format (12 characters: 2 letters + 9 alphanumeric + 1 digit)
     * 
     * @param isin ISIN code to validate
     * @return true if ISIN matches expected format
     */
    public boolean validateISINFormat(String isin) {
        if (isin == null || isin.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = isin.trim();
        // ISIN format: 12 chars (2 uppercase letters + 9 alphanumeric + 1 digit)
        return trimmed.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]{1}");
    }
}
