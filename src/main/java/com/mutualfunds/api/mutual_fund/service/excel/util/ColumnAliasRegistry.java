package com.mutualfunds.api.mutual_fund.service.excel.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry for managing column header aliases across different brokers
 * Maps variations of column names (e.g., "Scheme Name", "Fund Name") to standardized keys
 */
@Component
@Slf4j
public class ColumnAliasRegistry {
    
    private final Map<String, List<String>> aliasMap;
    
    public ColumnAliasRegistry() {
        this.aliasMap = new HashMap<>();
        initializeAliases();
    }
    
    /**
     * Initialize standard column aliases
     * Key: standardized column name (fundName, isin, units, currentValue)
     * Value: list of known aliases from various brokers
     */
    private void initializeAliases() {
        // Fund Name aliases
        aliasMap.put("fundName", Arrays.asList(
            "Scheme Name",
            "Fund Name",
            "Mutual Fund",
            "Scheme",
            "Fund",
            "Investment Name",
            "Portfolio"
        ));
        
        // ISIN aliases
        aliasMap.put("isin", Arrays.asList(
            "ISIN",
            "ISIN Code",
            "Code",
            "Scheme Code",
            "Investment Code",
            "ISN"
        ));
        
        // Units/Quantity aliases
        aliasMap.put("units", Arrays.asList(
            "Units Held",
            "Units",
            "Quantity",
            "Qty",
            "Quantity Held",
            "Holdings",
            "No. of Units",
            "Number of Units"
        ));
        
        // Current Value aliases
        aliasMap.put("currentValue", Arrays.asList(
            "Current Value",
            "Value",
            "Market Value",
            "Portfolio Value",
            "Current NAV",
            "Nav Value",
            "Current Amount",
            "Amount"
        ));
        
        // AMC (Asset Management Company) aliases
        aliasMap.put("amc", Arrays.asList(
            "AMC",
            "Asset Management Company",
            "Fund House",
            "Mutual Fund House",
            "Provider"
        ));
        
        // Category aliases
        aliasMap.put("category", Arrays.asList(
            "Category",
            "Fund Category",
            "Type",
            "Fund Type",
            "Asset Class"
        ));
        
        // Sub-category aliases
        aliasMap.put("subCategory", Arrays.asList(
            "Sub-category",
            "Sub Category",
            "Subcategory",
            "Sub-Type",
            "Style"
        ));
        
        // Folio Number aliases
        aliasMap.put("folioNumber", Arrays.asList(
            "Folio No.",
            "Folio No",
            "Folio",
            "Folio Number",
            "Account Number",
            "Account No."
        ));
        
        // Invested Value aliases
        aliasMap.put("investedValue", Arrays.asList(
            "Invested Value",
            "Investment Amount",
            "Cost",
            "Cost Value",
            "Purchase Value",
            "Investment Cost"
        ));
        
        // Returns aliases
        aliasMap.put("returns", Arrays.asList(
            "Returns",
            "Return %",
            "Return Percentage",
            "Gain %",
            "Gain/Loss",
            "Profit %"
        ));
        
        // XIRR aliases
        aliasMap.put("xirr", Arrays.asList(
            "XIRR",
            "XIRR %",
            "Annualized Return",
            "Annualized Return %",
            "Annual Return %",
            "CAGR"
        ));
    }
    
    /**
     * Find standardized column key for a detected header
     * 
     * @param detectedHeader Header from Excel file
     * @return Standardized key (e.g., "fundName") or null if not found
     */
    public String getStandardKeyForHeader(String detectedHeader) {
        if (detectedHeader == null || detectedHeader.trim().isEmpty()) {
            return null;
        }
        
        String normalizedHeader = detectedHeader.trim();
        
        for (Map.Entry<String, List<String>> entry : aliasMap.entrySet()) {
            for (String alias : entry.getValue()) {
                // Case-insensitive comparison
                if (alias.equalsIgnoreCase(normalizedHeader)) {
                    log.debug("Matched header '{}' to standard key '{}'", detectedHeader, entry.getKey());
                    return entry.getKey();
                }
            }
        }
        
        log.warn("No alias found for header: {}", detectedHeader);
        return null;
    }
    
    /**
     * Get all known aliases for a standard key
     * 
     * @param standardKey Standard column key (e.g., "fundName")
     * @return List of known aliases
     */
    public List<String> getAliasesForKey(String standardKey) {
        return aliasMap.getOrDefault(standardKey, Collections.emptyList());
    }
    
    /**
     * Check if a header is a known alias for any standard key
     * 
     * @param header Header to check
     * @return true if header is a known alias
     */
    public boolean isKnownAlias(String header) {
        return getStandardKeyForHeader(header) != null;
    }
    
    /**
     * Get all standard keys
     * 
     * @return Set of standard keys
     */
    public Set<String> getStandardKeys() {
        return aliasMap.keySet();
    }
}
