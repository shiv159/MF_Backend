package com.mutualfunds.api.mutual_fund.service.contract;

import com.mutualfunds.api.mutual_fund.dto.response.ParsedHoldingDto;

import java.util.List;

/**
 * Contract for file parsing service
 * Defines operations for extracting portfolio holdings from Excel files
 */
public interface IFileParsingService {
    
    /**
     * Parse file from byte array (Excel files only)
     * Supports .xlsx and .xls formats
     * Uses robust three-part extraction strategy with RobustExcelParser:
     * 1. Personal details from first 50 rows (exact label matching)
     * 2. Summary row with dynamic header-to-value mapping
     * 3. Holdings table with stricter detection (requires "Scheme Name" AND "Folio")
     * 
     * @param fileBytes Byte array of file content
     * @param fileType File type (e.g., "xlsx", "xls")
     * @return List of parsed holdings as ParsedHoldingDto objects with all columns mapped
     * @throws IllegalArgumentException if file type is not supported
     */
    List<ParsedHoldingDto> parseFileFromBytes(byte[] fileBytes, String fileType);
}
