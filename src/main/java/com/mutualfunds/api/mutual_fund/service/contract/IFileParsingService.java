package com.mutualfunds.api.mutual_fund.service.contract;

import java.util.List;
import java.util.Map;

/**
 * Contract for file parsing service
 * Defines operations for extracting portfolio holdings from various file formats
 */
public interface IFileParsingService {
    
    /**
     * Parse file from byte array
     * Supports .xlsx (Excel) and .pdf formats
     * 
     * @param fileBytes Byte array of file content
     * @param fileType File type (e.g., "xlsx", "pdf")
     * @return List of parsed holdings as Map objects with keys: fundName, isin, units, currentValue
     * @throws IllegalArgumentException if file type is not supported
     */
    List<Map<String, Object>> parseFileFromBytes(byte[] fileBytes, String fileType);
}
