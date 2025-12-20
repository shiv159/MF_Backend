package com.mutualfunds.api.mutual_fund.exception;

/**
 * Exception thrown when an external service call fails
 * Results in HTTP 503 Service Unavailable
 */
public class ServiceUnavailableException extends RuntimeException {

    private final String serviceName;

    public ServiceUnavailableException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public ServiceUnavailableException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * Create exception for external service failure
     */
    public static ServiceUnavailableException forService(String serviceName) {
        return new ServiceUnavailableException(
                serviceName,
                String.format("External service '%s' is currently unavailable", serviceName));
    }
}
