package com.mutualfunds.api.mutual_fund.exception;

/**
 * Exception thrown when access to a resource is forbidden
 * Results in HTTP 403 Forbidden
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
