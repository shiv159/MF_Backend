package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.dto.response.ParsedHoldingDto;
import com.mutualfunds.api.mutual_fund.service.contract.IFileParsingService;
import com.mutualfunds.api.mutual_fund.service.excel.ExcelParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Service for parsing Excel files
 * Extracts portfolio holding data from Excel files (.xlsx, .xls)
 * Returns strongly-typed ParsedHoldingDto objects for type safety
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileParsingService implements IFileParsingService {

    private final ExcelParser excelParser;

    /**
     * Parse file based on file type
     * Supports .xlsx (Excel) and .xls formats only
     *
     * @param filePath Path to the file to parse
     * @param fileType File type (e.g., "xlsx")
     * @return List of parsed holdings as ParsedHoldingDto objects
     * @throws IllegalArgumentException if file type is not supported
     */
    public List<ParsedHoldingDto> parseFile(Path filePath, String fileType) {
        log.info("Parsing file: {} with type: {}", filePath.getFileName(), fileType);

        if ("xlsx".equalsIgnoreCase(fileType) || "xls".equalsIgnoreCase(fileType)) {
            try {
                byte[] fileBytes = Files.readAllBytes(filePath);
                return excelParser.parse(fileBytes);
            } catch (IOException e) {
                log.error("Error reading Excel file: {}", filePath, e);
                throw new RuntimeException("Failed to read Excel file: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileType + ". Only .xlsx and .xls files are supported.");
        }
    }

    /**
     * Parse file from byte array (no file storage needed)
     * Supports .xlsx (Excel) and .xls formats only
     *
     * @param fileBytes Byte array of file content
     * @param fileType File type (e.g., "xlsx")
     * @return List of parsed holdings as ParsedHoldingDto objects
     * @throws IllegalArgumentException if file type is not supported
     */
    @Override
    public List<ParsedHoldingDto> parseFileFromBytes(byte[] fileBytes, String fileType) {
        log.info("Parsing file from bytes with type: {}", fileType);

        if ("xlsx".equalsIgnoreCase(fileType) || "xls".equalsIgnoreCase(fileType)) {
            try {
                return excelParser.parse(fileBytes);
            } catch (IOException e) {
                log.error("Error parsing Excel from bytes", e);
                throw new RuntimeException("Failed to parse Excel file: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileType + ". Only .xlsx and .xls files are supported.");
        }
    }
}

