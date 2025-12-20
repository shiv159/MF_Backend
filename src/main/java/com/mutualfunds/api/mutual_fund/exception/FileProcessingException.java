package com.mutualfunds.api.mutual_fund.exception;

/**
 * Exception thrown when a file operation fails
 * Results in HTTP 400 Bad Request
 */
public class FileProcessingException extends RuntimeException {

    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
