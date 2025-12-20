package com.mutualfunds.api.mutual_fund.exception;

/**
 * Exception thrown when a request contains invalid data or violates business
 * rules
 * Results in HTTP 400 Bad Request
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}