package com.mutualfunds.api.mutual_fund.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for parsing Excel and PDF files
 * Extracts portfolio holding data from various file formats
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileParsingService {

    /**
     * Parse file based on file type
     * Supports .xlsx (Excel) and .pdf formats
     *
     * @param filePath Path to the file to parse
     * @param fileType File type (e.g., "xlsx", "pdf")
     * @return List of parsed holdings as Map objects
     */
    public List<Map<String, Object>> parseFile(Path filePath, String fileType) {
        log.info("Parsing file: {} with type: {}", filePath.getFileName(), fileType);

        if ("xlsx".equalsIgnoreCase(fileType) || "xls".equalsIgnoreCase(fileType)) {
            return parseExcelFile(filePath);
        } else if ("pdf".equalsIgnoreCase(fileType)) {
            return parsePdfFile(filePath);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }
    }

    /**
     * Parse Excel file and extract holdings
     * Assumes standard portfolio format with columns: Fund Name, ISIN, Units, Current Value
     */
    private List<Map<String, Object>> parseExcelFile(Path filePath) {
        List<Map<String, Object>> holdings = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            log.debug("Reading Excel sheet: {}", sheet.getSheetName());

            // Skip header row
            boolean isHeader = true;
            int rowCount = 0;

            for (Row row : sheet) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                if (row.getPhysicalNumberOfCells() == 0) {
                    continue;
                }

                Map<String, Object> holding = new HashMap<>();

                // Extract columns
                String fundName = getCellValueAsString(row.getCell(0));
                String isin = getCellValueAsString(row.getCell(1));
                String units = getCellValueAsString(row.getCell(2));
                String currentValue = getCellValueAsString(row.getCell(3));

                if (!fundName.isEmpty() && !isin.isEmpty()) {
                    holding.put("fundName", fundName);
                    holding.put("isin", isin);
                    holding.put("units", parseDouble(units));
                    holding.put("currentValue", parseDouble(currentValue));
                    holding.put("source", "excel");

                    holdings.add(holding);
                    rowCount++;
                    log.debug("Parsed holding: {} (ISIN: {})", fundName, isin);
                }
            }

            log.info("Successfully parsed {} holdings from Excel file", rowCount);

        } catch (IOException e) {
            log.error("Error parsing Excel file: {}", filePath, e);
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return holdings;
    }

    /**
     * Parse PDF file and extract holdings
     * Attempts to extract table data or structured text from PDF
     */
    private List<Map<String, Object>> parsePdfFile(Path filePath) {
        List<Map<String, Object>> holdings = new ArrayList<>();

        try (PDDocument document = PDDocument.load(new File(filePath.toString()))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            log.debug("Extracted text from PDF: {} characters", text.length());

            // Parse holdings from text using regex patterns
            // Looking for patterns like: Fund Name, ISIN (12 chars), Units, Value
            Pattern fundPattern = Pattern.compile(
                    "([A-Za-z\\s]+)\\s+([A-Z]{2}[A-Z0-9]{9}[0-9]{1})\\s+([0-9.]+)\\s+([0-9.,]+)",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher matcher = fundPattern.matcher(text);
            int rowCount = 0;

            while (matcher.find()) {
                String fundName = matcher.group(1).trim();
                String isin = matcher.group(2).trim();
                double units = parseDouble(matcher.group(3));
                double currentValue = parseDouble(matcher.group(4));

                if (!fundName.isEmpty() && !isin.isEmpty() && isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]{1}")) {
                    Map<String, Object> holding = new HashMap<>();
                    holding.put("fundName", fundName);
                    holding.put("isin", isin);
                    holding.put("units", units);
                    holding.put("currentValue", currentValue);
                    holding.put("source", "pdf");

                    holdings.add(holding);
                    rowCount++;
                    log.debug("Parsed holding from PDF: {} (ISIN: {})", fundName, isin);
                }
            }

            log.info("Successfully parsed {} holdings from PDF file", rowCount);

        } catch (IOException e) {
            log.error("Error parsing PDF file: {}", filePath, e);
            throw new RuntimeException("Failed to parse PDF file: " + e.getMessage(), e);
        }

        return holdings;
    }

    /**
     * Extract cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * Parse double value, handling various formats
     */
    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }

        try {
            // Remove currency symbols and commas
            String cleaned = value.replaceAll("[₹$€£,]", "").trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Could not parse number: {}", value);
            return 0.0;
        }
    }
}
