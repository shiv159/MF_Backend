package com.mutualfunds.api.mutual_fund.exception;

/**
 * Exception thrown when a duplicate resource is attempted to be created
 * Results in HTTP 409 Conflict
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create exception for duplicate resource
     */
    public static DuplicateResourceException forResource(String resourceType, String field, Object value) {
        return new DuplicateResourceException(
                String.format("%s already exists with %s: %s", resourceType, field, value));
    }
}
