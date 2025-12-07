# Technical Documentation - File Parsing & ETL Enrichment Implementation

## Architecture Overview

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     OnboardingController                         │
│  - POST /api/onboarding/uploads (File Upload Endpoint)          │
│  - GET /api/onboarding/uploads/{uploadId} (Status Check)        │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ├──────────────────────┬──────────────────┐
                        ▼                      ▼                  ▼
            ┌──────────────────────┐  ┌─────────────────┐  ┌────────────┐
            │ FileParsingService   │  │ ETLEnrichment   │  │ Portfolio  │
            │                      │  │ Service         │  │ Upload     │
            │ - Excel Parsing      │  │                 │  │ Repository │
            │ - PDF Parsing        │  │ - ETL Client    │  │            │
            │ - Data Extraction    │  │ - Enrichment    │  │ (Entity)   │
            └──────────────────────┘  │   Logic         │  └────────────┘
                        │              └────────┬────────┘
                        │                       │
                        └───────────────┬───────┘
                                       ▼
                        ┌──────────────────────────┐
                        │   ETLClient              │
                        │ (Python FastAPI Service) │
                        │   POST /etl/enrich       │
                        └──────────────────────────┘
```

## Service Implementations

### FileParsingService

**Location**: `src/main/java/com/mutualfunds/api/mutual_fund/service/FileParsingService.java`

**Responsibilities**:
- Determine file type and delegate to appropriate parser
- Extract portfolio holdings from files
- Normalize and validate extracted data
- Handle parsing errors gracefully

**Key Methods**:

```java
public List<Map<String, Object>> parseFile(Path filePath, String fileType)
```

**Input Data Format**:
- File path to uploaded file
- File type indicator ("xlsx" or "pdf")

**Output Data Structure**:
```json
[
  {
    "fundName": "HDFC Equity Fund",
    "isin": "INF179K01LM8",
    "units": 100.5,
    "currentValue": 5025.75,
    "source": "excel"
  }
]
```

**Excel Parsing Details**:
- Uses Apache POI library (XSSFWorkbook for .xlsx)
- Expected column order: Fund Name | ISIN | Units | Current Value
- Handles numeric data with various decimal formats
- Skips empty rows and header rows

**PDF Parsing Details**:
- Uses Apache PDFBox library
- Pattern matching: `([A-Za-z\s]+)\s+([A-Z]{2}[A-Z0-9]{9}[0-9]{1})\s+([0-9.]+)\s+([0-9.,]+)`
- ISIN validation: `[A-Z]{2}[A-Z0-9]{9}[0-9]{1}`
- Supports various currency formats (₹, $, €, £)

### ETLEnrichmentService

**Location**: `src/main/java/com/mutualfunds/api/mutual_fund/service/ETLEnrichmentService.java`

**Responsibilities**:
- Prepare enrichment requests for Python ETL service
- Call external ETL service
- Handle response validation and conversion
- Track enrichment metrics

**Key Methods**:

```java
public List<Map<String, Object>> enrichPortfolioData(
    List<Map<String, Object>> parsedData,
    UUID userId
)
```

**Request Payload** (to Python ETL):
```json
{
  "uploadId": "uuid",
  "userId": "uuid",
  "parsedHoldings": [
    {
      "fundName": "HDFC Equity Fund",
      "isin": "INF179K01LM8",
      "units": 100.5,
      "currentValue": 5025.75
    }
  ],
  "enrichmentTimestamp": 1733596000000
}
```

**Response Payload** (from Python ETL):
```json
{
  "uploadId": "uuid",
  "status": "completed",
  "totalRecords": 5,
  "enrichedRecords": 5,
  "failedRecords": 0,
  "durationSeconds": 2,
  "enrichedFundsMaps": [
    {
      "fundName": "HDFC Equity Fund",
      "isin": "INF179K01LM8",
      "amc": "HDFC Asset Management",
      "category": "Large Cap",
      "units": 100.5,
      "nav": 50.12,
      "currentNav": 51.25,
      "expenseRatio": 0.55
    }
  ]
}
```

## Data Models

### PortfolioUpload Entity

```java
@Entity
@Table(name = "portfolio_uploads")
public class PortfolioUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uploadId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    private String fileName;          // Original file name
    private String fileType;          // "xlsx" or "pdf"
    private Long fileSize;            // Bytes
    private String filePath;          // Temp location
    private LocalDateTime uploadDate;
    
    @Enumerated(EnumType.STRING)
    private UploadStatus status;      // parsing, completed, failed
    
    private Integer parsedHoldingsCount;
    private Long processedRecords;    // After enrichment
    private String errorMessage;      // If failed
}
```

**Upload Status Enum**:
- `parsing`: Initial state, file being processed
- `completed`: Successfully enriched and stored
- `failed`: Error occurred during processing

### EnrichedFund DTO

```java
@Data
@Builder
public class EnrichedFund {
    // User's holding data
    private String fundName;
    private BigDecimal units;
    private BigDecimal nav;
    private BigDecimal value;
    
    // Fund master data (enriched)
    private String isin;
    private String amc;
    private String category;
    private Double expenseRatio;
    private Double currentNav;
    private LocalDate navAsOf;
    
    // Analytics (JSON columns)
    private JsonNode sectorAllocation;
    private JsonNode topHoldings;
}
```

## Async Processing Flow

### Request Handler

```java
@PostMapping("/uploads")
public ResponseEntity<UploadResponse> uploadPortfolio(@RequestBody UploadRequest request) {
    // 1. Save file to temp location
    // 2. Create PortfolioUpload record with status=PARSING
    // 3. Start async job
    // 4. Return immediate response with uploadId
}
```

### Async Job

```java
@Async
public void startUploadJob(UUID uploadId) {
    try {
        // 1. Get PortfolioUpload record
        // 2. Parse file using FileParsingService
        // 3. Enrich data using ETLEnrichmentService
        // 4. Update status to COMPLETED
        // 5. Store processedRecords count
    } catch (Exception e) {
        // Update status to FAILED with error message
        // Log detailed error
    }
}
```

### Status Polling

```java
@GetMapping("/uploads/{uploadId}")
public ResponseEntity<UploadResponse> getUploadStatus(@PathVariable UUID uploadId) {
    // Return current status and metrics
    // Client polls this endpoint for completion
}
```

## Error Handling Strategy

### File Parsing Errors

| Error | Handling |
|-------|----------|
| Invalid file format | Throw RuntimeException, log error, set status=failed |
| Corrupted file | Catch IOException, log details, mark upload failed |
| Empty file | Proceed with empty list, status=completed with 0 records |
| Missing columns | Skip invalid rows, process valid ones |
| Invalid ISIN | Filter out, log warning, continue processing |

### ETL Service Errors

| Error | Handling |
|-------|----------|
| Service unavailable | Throw RuntimeException, retry logic via message queue (future) |
| Invalid response | Log error, set status=failed with error message |
| Partial enrichment | Accept partial results if status=completed |
| Timeout | Depend on HTTP client timeout configuration |

## Configuration Requirements

### application.properties (Spring Boot)

```properties
# ETL Service Configuration
etl.service.url=http://localhost:8000
etl.enrich.endpoint=/etl/enrich

# File Upload Configuration
upload.temp.dir=${java.io.tmpdir}
upload.max.size=10485760  # 10MB

# Async Configuration
spring.task.execution.pool.core-size=2
spring.task.execution.pool.max-size=5
spring.task.execution.pool.queue-capacity=100
```

### Dependencies

**pom.xml entries**:
- Apache POI 5.0.0 (Excel parsing)
- Apache PDFBox 2.0.31 (PDF parsing)
- Spring Boot 3.3.13
- PostgreSQL driver for persistence

## Testing Strategy

### Unit Tests (Recommended)

```java
class FileParsingServiceTest {
    @Test void testExcelParsing() { }
    @Test void testPdfParsing() { }
    @Test void testInvalidFormat() { }
    @Test void testCurrencyHandling() { }
}

class ETLEnrichmentServiceTest {
    @Test void testSuccessfulEnrichment() { }
    @Test void testServiceUnavailable() { }
    @Test void testPartialEnrichment() { }
}
```

### Integration Tests

```java
class OnboardingIntegrationTest {
    @Test void testCompleteUploadFlow() { }
    @Test void testConcurrentUploads() { }
    @Test void testErrorRecovery() { }
}
```

### Test Data

**Sample Excel file**: 
- 3-5 rows of holdings
- Valid and invalid ISINs
- Various currency formats

**Sample PDF files**:
- Simple tabular format
- Complex multi-page document
- Scanned image (tesseract required)

## Performance Considerations

### Optimization Points

1. **File Parsing**:
   - Use streaming for large files
   - Cache parsed results temporarily
   - Parallel processing for multi-page PDFs (future)

2. **ETL Enrichment**:
   - Batch multiple uploads to reduce API calls
   - Connection pooling to ETL service
   - Response caching (short TTL)

3. **Database**:
   - Index on (user_id, uploadDate)
   - Index on (status) for queries
   - Archive old uploads

### Scalability

- Current: Single-threaded async processing
- Future: Message queue (Kafka/RabbitMQ) for distributed processing
- Future: Multi-instance deployment with shared storage

## Security Considerations

1. **File Upload**:
   - Validate file type by magic bytes (not extension)
   - Scan for malware (ClamAV integration future)
   - Set max file size limits

2. **Data Privacy**:
   - Encrypt sensitive data at rest
   - Use HTTPS for ETL service communication
   - Log audit trail of enrichment

3. **Authorization**:
   - Verify user ownership before processing
   - RBAC for admin operations

## Monitoring & Logging

### Key Metrics

```
- Total uploads received (counter)
- Files parsed successfully (counter)
- Average parsing time (histogram)
- ETL enrichment success rate (gauge)
- Failed uploads (counter)
- Processing queue depth (gauge)
```

### Log Levels

```
INFO:   Upload started, file parsed, enrichment completed
DEBUG:  Parsing progress, ETL request/response details
ERROR:  File parsing failure, ETL service error, database errors
WARN:   Partial enrichment, missing data, retry attempts
```

## Troubleshooting Guide

### Common Issues

| Issue | Solution |
|-------|----------|
| PDF parsing returns empty | Check text extraction enabled, try different library |
| ETL service timeout | Increase timeout, check service logs, verify network |
| Database constraint error | Check user exists, verify foreign keys |
| Async job not running | Check task executor configuration, verify @Async enabled |

---

**Document Version**: 1.0  
**Last Updated**: 2025-12-07  
**Status**: Complete
