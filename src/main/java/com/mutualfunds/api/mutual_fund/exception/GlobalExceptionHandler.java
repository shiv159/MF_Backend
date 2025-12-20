package com.mutualfunds.api.mutual_fund.exception;

import com.mutualfunds.api.mutual_fund.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.ArrayList;
import java.util.List;

/**
 * Global exception handler for all controllers
 * Provides consistent error responses and proper HTTP status codes
 * Prevents internal error details from being exposed to clients
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        /**
         * Handle validation errors (e.g., @Valid annotation failures)
         * Returns 400 Bad Request with field-level error details
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationErrors(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {

                log.warn("Validation error on {}: {}", request.getRequestURI(), ex.getMessage());

                List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
                for (FieldError error : ex.getBindingResult().getFieldErrors()) {
                        fieldErrors.add(new ErrorResponse.FieldError(
                                        error.getField(),
                                        error.getDefaultMessage(),
                                        error.getRejectedValue()));
                }

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("VALIDATION_ERROR")
                                .message("Invalid input data")
                                .path(request.getRequestURI())
                                .fieldErrors(fieldErrors)
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        /**
         * Handle BadRequestException
         * Returns 400 Bad Request
         */
        @ExceptionHandler(BadRequestException.class)
        public ResponseEntity<ErrorResponse> handleBadRequest(
                        BadRequestException ex,
                        HttpServletRequest request) {

                log.warn("Bad request on {}: {}", request.getRequestURI(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("BAD_REQUEST")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        /**
         * Handle ResourceNotFoundException
         * Returns 404 Not Found
         */
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleNotFound(
                        ResourceNotFoundException ex,
                        HttpServletRequest request) {

                log.warn("Resource not found on {}: {}", request.getRequestURI(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.NOT_FOUND.value())
                                .error("NOT_FOUND")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        /**
         * Handle ForbiddenException and SecurityException
         * Returns 403 Forbidden
         */
        @ExceptionHandler({ ForbiddenException.class, SecurityException.class })
        public ResponseEntity<ErrorResponse> handleForbidden(
                        RuntimeException ex,
                        HttpServletRequest request) {

                log.warn("Access forbidden on {}: {}", request.getRequestURI(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.FORBIDDEN.value())
                                .error("FORBIDDEN")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }

        /**
         * Handle authentication errors
         * Returns 401 Unauthorized
         */
        @ExceptionHandler({ AuthenticationException.class, BadCredentialsException.class })
        public ResponseEntity<ErrorResponse> handleAuthenticationError(
                        Exception ex,
                        HttpServletRequest request) {

                log.warn("Authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.UNAUTHORIZED.value())
                                .error("AUTHENTICATION_FAILED")
                                .message("Invalid credentials or authentication required")
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }

        /**
         * Handle file upload size exceeded
         * Returns 413 Payload Too Large
         */
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
                        MaxUploadSizeExceededException ex,
                        HttpServletRequest request) {

                log.warn("File size exceeded on {}: {}", request.getRequestURI(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                                .error("FILE_TOO_LARGE")
                                .message("File size exceeds maximum allowed limit")
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
        }

        /**
         * Handle file processing errors
         * Returns 400 Bad Request
         */
        @ExceptionHandler(FileProcessingException.class)
        public ResponseEntity<ErrorResponse> handleFileProcessingError(
                        FileProcessingException ex,
                        HttpServletRequest request) {

                log.error("File processing error on {}: {}", request.getRequestURI(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("FILE_PROCESSING_ERROR")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        /**
         * Handle IllegalArgumentException
         * Returns 400 Bad Request
         */
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleIllegalArgument(
                        IllegalArgumentException ex,
                        HttpServletRequest request) {

                log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("INVALID_ARGUMENT")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        /**
         * Handle UnauthorizedException
         * Returns 401 Unauthorized
         */
        @ExceptionHandler(UnauthorizedException.class)
        public ResponseEntity<ErrorResponse> handleUnauthorized(
                        UnauthorizedException ex,
                        HttpServletRequest request) {

                log.warn("Unauthorized access on {}: {}", request.getRequestURI(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.UNAUTHORIZED.value())
                                .error("UNAUTHORIZED")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }

        /**
         * Handle DuplicateResourceException
         * Returns 409 Conflict
         */
        @ExceptionHandler(DuplicateResourceException.class)
        public ResponseEntity<ErrorResponse> handleDuplicateResource(
                        DuplicateResourceException ex,
                        HttpServletRequest request) {

                log.warn("Duplicate resource on {}: {}", request.getRequestURI(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.CONFLICT.value())
                                .error("DUPLICATE_RESOURCE")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }

        /**
         * Handle ServiceUnavailableException
         * Returns 503 Service Unavailable
         */
        @ExceptionHandler(ServiceUnavailableException.class)
        public ResponseEntity<ErrorResponse> handleServiceUnavailable(
                        ServiceUnavailableException ex,
                        HttpServletRequest request) {

                log.error("Service unavailable on {}: {} - {}",
                                request.getRequestURI(), ex.getServiceName(), ex.getMessage());

                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                                .error("SERVICE_UNAVAILABLE")
                                .message(ex.getMessage())
                                .details("Service: " + ex.getServiceName())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }

        /**
         * Handle all other unexpected exceptions
         * Returns 500 Internal Server Error
         * IMPORTANT: Does NOT expose internal error details to client
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGenericError(
                        Exception ex,
                        HttpServletRequest request) {

                // Log full stack trace for debugging
                log.error("Unexpected error on {}", request.getRequestURI(), ex);

                // Return generic error message to client (don't expose internal details)
                ErrorResponse errorResponse = ErrorResponse.builder()
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .error("INTERNAL_SERVER_ERROR")
                                .message("An unexpected error occurred. Please try again later.")
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
}