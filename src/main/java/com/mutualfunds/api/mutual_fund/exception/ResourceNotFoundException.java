package com.mutualfunds.api.mutual_fund.exception;

/**
 * Exception thrown when a requested resource cannot be found
 * Results in HTTP 404 Not Found
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create exception with resource type and ID
     */
    public static ResourceNotFoundException forResource(String resourceType, Object id) {
        return new ResourceNotFoundException(
                String.format("%s not found with ID: %s", resourceType, id));
    }
}