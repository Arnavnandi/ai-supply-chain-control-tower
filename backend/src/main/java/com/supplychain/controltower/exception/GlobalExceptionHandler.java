package com.supplychain.controltower.exception;

import com.supplychain.controltower.service.email.EmailDeliveryUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError ? ((FieldError) error).getField() : error.getObjectName();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("[VALIDATION EXCEPTION] Request parameter validation failed: {}", errors);

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Validation failed for request parameters")
                .validationErrors(errors)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("[RESOURCE NOT FOUND EXCEPTION] {}", ex.getMessage());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorDto);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("[ACCESS DENIED EXCEPTION] {}", ex.getMessage());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("Access denied: You do not have permission to perform this operation.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorDto);
    }

    @ExceptionHandler(TenantContextMissingException.class)
    public ResponseEntity<ErrorResponseDto> handleTenantContextMissingException(TenantContextMissingException ex) {
        log.warn("[TENANT CONTEXT MISSING] {}", ex.getMessage());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorDto);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("[ILLEGAL ARGUMENT EXCEPTION] {}", ex.getMessage());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("[AUTHENTICATION EXCEPTION] Bad credentials or invalid verification: {}", ex.getMessage());

        String clientMessage = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "Invalid credentials or verification session.";

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(clientMessage)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorDto);
    }

    @ExceptionHandler(EmailDeliveryUnavailableException.class)
    public ResponseEntity<ErrorResponseDto> handleEmailDeliveryUnavailableException(EmailDeliveryUnavailableException ex) {
        log.warn("[EMAIL DELIVERY UNAVAILABLE] {}", ex.getMessage());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Unavailable")
                .message("Email delivery service is currently unavailable. Please contact support or verify staging SMTP relay / Brevo API configuration.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorDto);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGlobalException(Exception ex) {
        log.error("[UNEXPECTED SYSTEM EXCEPTION]", ex);

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please contact support.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
    }
}
