package com.mutualfunds.api.mutual_fund.service.excel;

import com.mutualfunds.api.mutual_fund.dto.response.ParsedHoldingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * High-level Excel parser orchestrator
 * Uses robust three-part extraction strategy for broker portfolio statements:
 * 1. Personal details from first 50 rows (exact label matching)
 * 2. Summary row with dynamic header-to-value mapping
 * 3. Holdings table with stricter detection (requires "Scheme Name" AND "Folio")
 * 
 * Returns strongly-typed ParsedHoldingDto objects for type safety and validation.
 * This replaces the previous Tika+Jsoup approach which failed on broker formats
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelParser {
    
    private final RobustExcelParser robustExcelParser;
    
    /**
     * Parse Excel file and return list of normalized holdings as DTOs
     * Uses robust three-part extraction strategy for broker portfolio statements
     * 
     * @param fileBytes Excel file content as byte array
     * @return List of parsed holdings as ParsedHoldingDto objects
     * @throws IOException if parsing fails
     */
    public List<ParsedHoldingDto> parse(byte[] fileBytes) throws IOException {
        log.info("Starting Excel parsing from {} bytes using RobustExcelParser", fileBytes.length);
        
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
             XSSFWorkbook workbook = new XSSFWorkbook(bais)) {
            
            var sheet = workbook.getSheetAt(0);
            log.debug("Reading Excel sheet: {}", sheet.getSheetName());
            
            // Step 1: Extract personal details (first 50 rows)
            Map<String, String> personalDetails = robustExcelParser.extractPersonalDetails(sheet);
            if (!personalDetails.isEmpty()) {
                log.debug("Extracted personal details: {}", personalDetails.keySet());
            }
            
            // Step 2: Extract summary row
            Map<String, String> summaryRow = robustExcelParser.extractSummaryRow(sheet);
            if (!summaryRow.isEmpty()) {
                log.debug("Extracted summary row: {}", summaryRow.keySet());
            }
            
            // Step 3: Extract holdings table (main data) - returns ParsedHoldingDto
            List<ParsedHoldingDto> holdings = robustExcelParser.extractHoldingsTable(sheet);
            
            log.info("Successfully parsed Excel file: {} personal details, {} summary fields, {} holdings",
                    personalDetails.size(), summaryRow.size(), holdings.size());
            
            return holdings;
            
        } catch (IOException e) {
            log.error("Error parsing Excel file: {}", e.getMessage(), e);
            throw new IOException("Failed to parse Excel file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during Excel parsing", e);
            throw new IOException("Error parsing Excel file: " + e.getMessage(), e);
        }
    }

}
