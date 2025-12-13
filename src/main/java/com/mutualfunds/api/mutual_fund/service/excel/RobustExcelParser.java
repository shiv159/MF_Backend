package com.mutualfunds.api.mutual_fund.service.excel;

import com.mutualfunds.api.mutual_fund.dto.response.ParsedHoldingDto;
import com.mutualfunds.api.mutual_fund.service.excel.util.ColumnAliasRegistry;
import com.mutualfunds.api.mutual_fund.service.excel.util.ExcelFormatValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Robust Excel parser for broker portfolio statements
 * Three-part extraction strategy:
 * 1. Extract personal details from first 50 rows using exact label matching
 * 2. Extract summary row with dynamic header-to-value mapping
 * 3. Extract holdings table with stricter detection (requires "Scheme Name" AND "Folio")
 * 
 * Advantages over Tika+Jsoup approach:
 * - No Tika dependency issues
 * - Handles broker format variations automatically
 * - Dynamic column detection instead of hardcoded positions
 * - Better resource management (single DataFormatter instance)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobustExcelParser {
    
    private final ColumnAliasRegistry columnAliasRegistry;
    private final ExcelFormatValidator formatValidator;
    
    /**
     * Extract personal details from Excel sheet (first 50 rows)
     * Uses exact label matching to find values like name, email, phone
     * 
     * @param sheet Excel sheet to extract from
     * @return Map with keys: firstName, lastName, email, phone, panNumber, etc.
     */
    public Map<String, String> extractPersonalDetails(Sheet sheet) {
        Map<String, String> details = new HashMap<>();
        DataFormatter formatter = new DataFormatter();
        
        int rowLimit = Math.min(50, sheet.getLastRowNum() + 1);
        
        for (int i = 0; i < rowLimit; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            Cell labelCell = row.getCell(0);
            Cell valueCell = row.getCell(1);
            
            if (labelCell == null || valueCell == null) continue;
            
            String label = formatter.formatCellValue(labelCell).trim().toLowerCase();
            String value = formatter.formatCellValue(valueCell).trim();
            
            if (value.isEmpty()) continue;
            
            // Map common labels to standardized keys
            if (label.contains("name") && !label.contains("scheme")) {
                details.put("name", value);
            } else if (label.contains("email") || label.contains("mail")) {
                details.put("email", value);
            } else if (label.contains("phone") || label.contains("mobile")) {
                details.put("phone", value);
            } else if (label.contains("pan") || label.contains("pan number")) {
                details.put("pan", value);
            } else if (label.contains("aadhar") || label.contains("aadhaar")) {
                details.put("aadhar", value);
            } else if (label.contains("dob") || label.contains("date of birth")) {
                details.put("dob", value);
            } else if (label.contains("address")) {
                details.put("address", value);
            }
        }
        
        log.debug("Extracted personal details: {}", details.keySet());
        return details;
    }
    
    /**
     * Extract summary row containing portfolio totals
     * Dynamically finds the summary row and maps headers to values
     * Looks for rows containing "Total Investments" or "Current Portfolio Value"
     * 
     * @param sheet Excel sheet to extract from
     * @return Map with keys: totalInvestment, currentPortfolioValue, gains, etc.
     */
    public Map<String, String> extractSummaryRow(Sheet sheet) {
        Map<String, String> summary = new HashMap<>();
        DataFormatter formatter = new DataFormatter();
        
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            // Look for summary row indicators
            boolean isSummaryRow = false;
            for (int j = 0; j < row.getPhysicalNumberOfCells(); j++) {
                Cell cell = row.getCell(j);
                if (cell == null) continue;
                
                String cellValue = formatter.formatCellValue(cell).toLowerCase().trim();
                if (cellValue.contains("total investment") || 
                    cellValue.contains("current portfolio") ||
                    cellValue.contains("total value")) {
                    isSummaryRow = true;
                    break;
                }
            }
            
            if (!isSummaryRow) continue;
            
            // Found summary row - extract all values
            for (int j = 0; j < row.getPhysicalNumberOfCells(); j++) {
                Cell headerCell = row.getCell(j);
                if (headerCell == null) continue;
                
                String header = formatter.formatCellValue(headerCell).trim().toLowerCase();
                if (header.isEmpty()) continue;
                
                // Try to get value from next column
                Cell valueCell = row.getCell(j + 1);
                if (valueCell != null) {
                    String value = formatter.formatCellValue(valueCell).trim();
                    
                    if (header.contains("total investment")) {
                        summary.put("totalInvestment", value);
                    } else if (header.contains("current portfolio") || header.contains("current value")) {
                        summary.put("currentPortfolioValue", value);
                    } else if (header.contains("gain") || header.contains("loss")) {
                        summary.put("gains", value);
                    } else if (header.contains("return")) {
                        summary.put("returns", value);
                    }
                }
            }
            
            // Found and processed summary - don't continue
            if (!summary.isEmpty()) {
                log.debug("Extracted summary row: {}", summary);
                return summary;
            }
        }
        
        log.debug("No summary row found in spreadsheet");
        return summary;
    }
    
    /**
     * Extract holdings table from Excel sheet
     * Stricter detection: requires BOTH "Scheme Name" AND "Folio" columns to be present
     * This eliminates false positive table detection
     * 
     * @param sheet Excel sheet to extract from
     * @return List of holdings as ParsedHoldingDto with all columns mapped
     */
    public List<ParsedHoldingDto> extractHoldingsTable(Sheet sheet) {
        List<ParsedHoldingDto> holdings = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        
        // Step 1: Find header row by looking for "Scheme Name" AND "Folio"
        int headerRowIndex = -1;
        Map<String, Integer> columnIndexMap = new HashMap<>();
        
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            boolean hasSchemeColumn = false;
            boolean hasFolioColumn = false;
            
            for (int j = 0; j < row.getPhysicalNumberOfCells(); j++) {
                Cell cell = row.getCell(j);
                if (cell == null) continue;
                
                String cellValue = formatter.formatCellValue(cell).toLowerCase().trim();
                
                // Check for scheme column
                if (cellValue.contains("scheme")) {
                    hasSchemeColumn = true;
                }
                
                // Check for folio column
                if (cellValue.contains("folio")) {
                    hasFolioColumn = true;
                }
            }
            
            // Both required columns must be present in same row
            if (hasSchemeColumn && hasFolioColumn) {
                headerRowIndex = i;
                break;
            }
        }
        
        if (headerRowIndex == -1) {
            log.warn("Holdings table not found: missing 'Scheme Name' or 'Folio' column");
            return holdings;
        }
        
        log.debug("Found holdings table header at row {}", headerRowIndex);
        
        // Step 2: Build column index map from header row
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) {
            log.warn("Header row is null at index {}", headerRowIndex);
            return holdings;
        }
        
        for (int j = 0; j < headerRow.getPhysicalNumberOfCells(); j++) {
            Cell cell = headerRow.getCell(j);
            if (cell == null) continue;
            
            String headerText = formatter.formatCellValue(cell).trim();
            String standardKey = columnAliasRegistry.getStandardKeyForHeader(headerText);
            
            if (standardKey != null) {
                columnIndexMap.put(standardKey, j);
                log.debug("Mapped column: {} → {} (index {})", headerText, standardKey, j);
            }
        }
        
        // Validate minimum columns
        if (columnIndexMap.size() < 2) {
            log.warn("Holdings table has insufficient columns: found {}, need at least 2", columnIndexMap.size());
            return holdings;
        }
        
        // Step 3: Parse data rows
        int parsedCount = 0;
        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row dataRow = sheet.getRow(i);
            if (dataRow == null || dataRow.getPhysicalNumberOfCells() == 0) {
                continue;
            }
            
            // Check if row is empty or should stop parsing
            Cell firstCell = dataRow.getCell(0);
            if (firstCell == null) {
                // Empty row - might signal end of holdings table
                if (parsedCount > 0) {
                    log.debug("Stopping holdings table parsing at empty row {}", i);
                    break;
                }
                continue;
            }
            
            String firstValue = formatter.formatCellValue(firstCell).trim();
            if (firstValue.isEmpty() && parsedCount > 0) {
                log.debug("Stopping holdings table parsing at empty first cell, row {}", i);
                break;
            }
            
            // Extract holding from row into DTO
            ParsedHoldingDto.ParsedHoldingDtoBuilder holdingBuilder = ParsedHoldingDto.builder()
                .source("excel");
            
            // Extract all columns
            if (columnIndexMap.containsKey("fundName")) {
                String fundName = getCellValue(dataRow, columnIndexMap.get("fundName"), formatter).trim();
                holdingBuilder.fundName(fundName);
            }
            
            if (columnIndexMap.containsKey("isin")) {
                String isin = getCellValue(dataRow, columnIndexMap.get("isin"), formatter).trim();
                holdingBuilder.isin(isin);
            }
            
            if (columnIndexMap.containsKey("amc")) {
                String amc = getCellValue(dataRow, columnIndexMap.get("amc"), formatter).trim();
                holdingBuilder.amc(amc);
            }
            
            if (columnIndexMap.containsKey("category")) {
                String category = getCellValue(dataRow, columnIndexMap.get("category"), formatter).trim();
                holdingBuilder.category(category);
            }
            
            if (columnIndexMap.containsKey("subCategory")) {
                String subCategory = getCellValue(dataRow, columnIndexMap.get("subCategory"), formatter).trim();
                holdingBuilder.subCategory(subCategory);
            }
            
            if (columnIndexMap.containsKey("folioNumber")) {
                String folioNumber = getCellValue(dataRow, columnIndexMap.get("folioNumber"), formatter).trim();
                holdingBuilder.folioNumber(folioNumber);
            }
            
            if (columnIndexMap.containsKey("units")) {
                String unitsStr = getCellValue(dataRow, columnIndexMap.get("units"), formatter);
                holdingBuilder.units(parseNumeric(unitsStr));
            }
            
            if (columnIndexMap.containsKey("investedValue")) {
                String investedStr = getCellValue(dataRow, columnIndexMap.get("investedValue"), formatter);
                holdingBuilder.investedValue(parseNumeric(investedStr));
            }
            
            if (columnIndexMap.containsKey("currentValue")) {
                String valueStr = getCellValue(dataRow, columnIndexMap.get("currentValue"), formatter);
                holdingBuilder.currentValue(parseNumeric(valueStr));
            }
            
            if (columnIndexMap.containsKey("returns")) {
                String returnsStr = getCellValue(dataRow, columnIndexMap.get("returns"), formatter);
                holdingBuilder.returns(parseNumeric(returnsStr));
            }
            
            if (columnIndexMap.containsKey("xirr")) {
                String xirrStr = getCellValue(dataRow, columnIndexMap.get("xirr"), formatter);
                holdingBuilder.xirr(parseNumeric(xirrStr));
            }
            
            ParsedHoldingDto holding = holdingBuilder.build();
            
            // Validate and add holding
            if (formatValidator.validateHolding(holding)) {
                holdings.add(holding);
                parsedCount++;
                log.debug("Parsed holding #{}: {}", parsedCount, holding.getFundName());
            } else {
                log.debug("Skipped invalid holding row at index {}", i);
            }
        }
        
        log.info("Successfully extracted {} holdings from Excel", parsedCount);
        return holdings;
    }
    
    /**
     * Helper: Get cell value as string with proper formatting
     * 
     * @param row Excel row
     * @param columnIndex Column index
     * @param formatter DataFormatter instance
     * @return Cell value as string, or empty string if null
     */
    private String getCellValue(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell);
    }
    
    /**
     * Helper: Parse numeric value from string, handling currency symbols
     * 
     * @param value String value to parse
     * @return Parsed double, or 0.0 if parsing fails
     */
    private double parseNumeric(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        
        try {
            // Remove currency symbols and commas
            String cleaned = value
                    .replaceAll("[₹$€£]", "")
                    .replaceAll(",", "")
                    .trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.debug("Failed to parse numeric value: {}", value);
            return 0.0;
        }
    }
}
