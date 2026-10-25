package com.supplychain.controltower.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Warehouse not found with id: 99");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleResourceNotFoundException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Not Found", response.getBody().getError());
        assertEquals("Warehouse not found with id: 99", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void testHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden action");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleAccessDeniedException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Forbidden", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("Access denied"));
    }

    @Test
    void testHandleValidationExceptions() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("productDto", "price", "Price must be greater than zero");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertNotNull(response.getBody().getValidationErrors());
        assertEquals("Price must be greater than zero", response.getBody().getValidationErrors().get("price"));
    }

    @Test
    void testHandleBadCredentialsException_WithCustomMessage() {
        org.springframework.security.authentication.BadCredentialsException ex =
                new org.springframework.security.authentication.BadCredentialsException("Invalid verification code. 2 attempt(s) remaining.");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleBadCredentialsException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized", response.getBody().getError());
        assertEquals("Invalid verification code. 2 attempt(s) remaining.", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void testHandleBadCredentialsException_WithExpiredMessage() {
        org.springframework.security.authentication.BadCredentialsException ex =
                new org.springframework.security.authentication.BadCredentialsException("Verification code has expired. Please request a new code.");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleBadCredentialsException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized", response.getBody().getError());
        assertEquals("Verification code has expired. Please request a new code.", response.getBody().getMessage());
    }

    @Test
    void testHandleBadCredentialsException_WithBlankMessage_FallsBackSafely() {
        org.springframework.security.authentication.BadCredentialsException ex =
                new org.springframework.security.authentication.BadCredentialsException("");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleBadCredentialsException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized", response.getBody().getError());
        assertEquals("Invalid credentials or verification session.", response.getBody().getMessage());
    }

    @Test
    void testHandleEmailDeliveryUnavailableException_ReturnsServiceUnavailable() {
        com.supplychain.controltower.service.email.EmailDeliveryUnavailableException ex =
                new com.supplychain.controltower.service.email.EmailDeliveryUnavailableException(
                        "Internal details: SMTP host connect failed to smtp.brevo.com:587 with key secret_12345");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleEmailDeliveryUnavailableException(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().getStatus());
        assertEquals("Service Unavailable", response.getBody().getError());
        // Verify sensitive credentials/hosts are not leaked in client response message
        assertFalse(response.getBody().getMessage().contains("secret_12345"));
        assertFalse(response.getBody().getMessage().contains("smtp.brevo.com"));
        assertTrue(response.getBody().getMessage().contains("Email delivery service is currently unavailable"));
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void testHandleGlobalException() {
        Exception ex = new RuntimeException("Unexpected DB connection pool exhausted");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleGlobalException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("Internal Server Error", response.getBody().getError());
        assertEquals("An unexpected error occurred. Please contact support.", response.getBody().getMessage());
    }
}
