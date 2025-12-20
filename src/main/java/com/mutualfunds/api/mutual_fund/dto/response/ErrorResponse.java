package com.mutualfunds.api.mutual_fund.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardized error response for all API errors
 * Provides consistent error structure across the application
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * HTTP status code
     */
    private int status;

    /**
     * Error code for client-side handling (e.g., "USER_NOT_FOUND", "INVALID_FILE")
     */
    private String error;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Detailed error description (optional, for debugging)
     */
    private String details;

    /**
     * API endpoint path where error occurred
     */
    private String path;

    /**
     * Timestamp when error occurred
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Validation errors (for field-level validation failures)
     */
    private List<FieldError> fieldErrors;

    /**
     * Field-level validation error
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }

    /**
     * Simple constructor for basic errors
     */
    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
