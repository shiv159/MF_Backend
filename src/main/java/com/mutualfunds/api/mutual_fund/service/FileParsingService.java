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
     * Parse file from byte array (no file storage needed)
     * Supports .xlsx (Excel) and .pdf formats
     *
     * @param fileBytes Byte array of file content
     * @param fileType File type (e.g., "xlsx", "pdf")
     * @return List of parsed holdings as Map objects
     */
    public List<Map<String, Object>> parseFileFromBytes(byte[] fileBytes, String fileType) {
        log.info("Parsing file from bytes with type: {}", fileType);

        if ("xlsx".equalsIgnoreCase(fileType) || "xls".equalsIgnoreCase(fileType)) {
            return parseExcelFromBytes(fileBytes);
        } else if ("pdf".equalsIgnoreCase(fileType)) {
            return parsePdfFromBytes(fileBytes);
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
     * Parse Excel file from byte array
     */
    private List<Map<String, Object>> parseExcelFromBytes(byte[] fileBytes) {
        List<Map<String, Object>> holdings = new ArrayList<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
             Workbook workbook = new XSSFWorkbook(bais)) {

            Sheet sheet = workbook.getSheetAt(0);
            log.debug("Reading Excel sheet: {}", sheet.getSheetName());

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

            log.info("Successfully parsed {} holdings from Excel bytes", rowCount);

        } catch (IOException e) {
            log.error("Error parsing Excel from bytes", e);
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

        try {
            PDDocument document = org.apache.pdfbox.Loader.loadPDF(filePath.toFile());
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();

            log.debug("Extracted text from PDF: {} characters", text.length());
            log.debug("PDF text preview: {}", text.substring(0, Math.min(500, text.length())));

            // Parse holdings from text using multiple regex patterns to handle various formats
            // Pattern 1: Fund Name + ISIN (on same or next line) + Units + Value
            Pattern isinPattern = Pattern.compile("\\b[A-Z]{2}[A-Z0-9]{9}[0-9]{1}\\b");
            
            int rowCount = 0;
            List<String> lines = Arrays.asList(text.split("\\n"));
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                Matcher isinMatcher = isinPattern.matcher(line);
                
                if (isinMatcher.find()) {
                    String isin = isinMatcher.group().trim();
                    
                    // Extract potential fund name (text before ISIN)
                    String beforeIsin = line.substring(0, isinMatcher.start()).trim();
                    String afterIsin = line.substring(isinMatcher.end()).trim();
                    
                    // Try to extract units and value from after ISIN or next line
                    String fundName = beforeIsin.isEmpty() && i > 0 ? 
                            lines.get(i - 1).trim() : beforeIsin;
                    
                    // Extract numbers from afterIsin
                    Pattern numberPattern = Pattern.compile("([0-9.]+)");
                    Matcher numberMatcher = numberPattern.matcher(afterIsin);
                    
                    List<Double> numbers = new ArrayList<>();
                    while (numberMatcher.find()) {
                        numbers.add(parseDouble(numberMatcher.group(1)));
                    }
                    
                    // If we have enough numbers and a fund name with ISIN
                    if (!fundName.isEmpty() && numbers.size() >= 1 && 
                        isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]{1}")) {
                        
                        Map<String, Object> holding = new HashMap<>();
                        holding.put("fundName", fundName);
                        holding.put("isin", isin);
                        holding.put("units", numbers.size() > 0 ? numbers.get(0) : 0.0);
                        holding.put("currentValue", numbers.size() > 1 ? numbers.get(1) : 0.0);
                        holding.put("source", "pdf");

                        holdings.add(holding);
                        rowCount++;
                        log.debug("Parsed holding from PDF: {} (ISIN: {})", fundName, isin);
                    }
                }
            }

            log.info("Successfully parsed {} holdings from PDF file", rowCount);

        } catch (IOException e) {
            log.error("Error parsing PDF file: {}", filePath, e);
            throw new RuntimeException("Failed to parse PDF file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error parsing PDF file: {}", filePath, e);
            throw new RuntimeException("Failed to parse PDF file: " + e.getMessage(), e);
        }

        return holdings;
    }

    /**
     * Parse PDF file from byte array
     */
    private List<Map<String, Object>> parsePdfFromBytes(byte[] fileBytes) {
        List<Map<String, Object>> holdings = new ArrayList<>();

        try {
            PDDocument document = org.apache.pdfbox.Loader.loadPDF(fileBytes);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();

            log.debug("Extracted text from PDF: {} characters", text.length());
            log.debug("PDF text preview: {}", text.substring(0, Math.min(500, text.length())));

            // Parse holdings from text using multiple regex patterns to handle various formats
            Pattern isinPattern = Pattern.compile("\\b[A-Z]{2}[A-Z0-9]{9}[0-9]{1}\\b");
            
            int rowCount = 0;
            List<String> lines = Arrays.asList(text.split("\\n"));
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                Matcher isinMatcher = isinPattern.matcher(line);
                
                if (isinMatcher.find()) {
                    String isin = isinMatcher.group().trim();
                    
                    // Extract potential fund name (text before ISIN)
                    String beforeIsin = line.substring(0, isinMatcher.start()).trim();
                    String afterIsin = line.substring(isinMatcher.end()).trim();
                    
                    // Try to extract units and value from after ISIN or next line
                    String fundName = beforeIsin.isEmpty() && i > 0 ? 
                            lines.get(i - 1).trim() : beforeIsin;
                    
                    // Extract numbers from afterIsin
                    Pattern numberPattern = Pattern.compile("([0-9.]+)");
                    Matcher numberMatcher = numberPattern.matcher(afterIsin);
                    
                    List<Double> numbers = new ArrayList<>();
                    while (numberMatcher.find()) {
                        numbers.add(parseDouble(numberMatcher.group(1)));
                    }
                    
                    // If we have enough numbers and a fund name with ISIN
                    if (!fundName.isEmpty() && numbers.size() >= 1 && 
                        isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]{1}")) {
                        
                        Map<String, Object> holding = new HashMap<>();
                        holding.put("fundName", fundName);
                        holding.put("isin", isin);
                        holding.put("units", numbers.size() > 0 ? numbers.get(0) : 0.0);
                        holding.put("currentValue", numbers.size() > 1 ? numbers.get(1) : 0.0);
                        holding.put("source", "pdf");

                        holdings.add(holding);
                        rowCount++;
                        log.debug("Parsed holding from PDF: {} (ISIN: {})", fundName, isin);
                    }
                }
            }

            log.info("Successfully parsed {} holdings from PDF bytes", rowCount);

        } catch (IOException e) {
            log.error("Error parsing PDF from bytes", e);
            throw new RuntimeException("Failed to parse PDF file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error parsing PDF from bytes", e);
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
