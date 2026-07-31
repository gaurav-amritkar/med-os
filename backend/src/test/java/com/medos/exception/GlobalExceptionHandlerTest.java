package com.medos.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest req(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void handleBusiness_returnsConfiguredStatus() {
        BusinessException ex = new BusinessException(HttpStatus.CONFLICT, "duplicate uhid");
        ResponseEntity<ApiError> response = handler.handleBusiness(ex, req("/api/patients"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("duplicate uhid", response.getBody().getMessage());
        assertEquals("/api/patients", response.getBody().getPath());
    }

    @Test
    void handleBusiness_defaultStatus_isBadRequest() {
        BusinessException ex = new BusinessException("bad input");
        ResponseEntity<ApiError> response = handler.handleBusiness(ex, req("/x"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Bad Request", response.getBody().getError());
    }

    @Test
    void handleResourceNotFound_isSubclassOfBusiness_returns404() {
        // ResourceNotFoundException extends BusinessException, so the broader BusinessException handler wins.
        ResourceNotFoundException ex = new ResourceNotFoundException("Patient", "abc");
        ResponseEntity<ApiError> response = handler.handleBusiness(ex, req("/api/patients/abc"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("Patient not found"));
    }

    @Test
    void handleBadCredentials_returns401WithGenericMessage() {
        ResponseEntity<ApiError> response = handler.handleBadCreds(new BadCredentialsException("..."), req("/api/auth/login"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Invalid credentials", response.getBody().getMessage());
    }

    @Test
    void handleDenied_returns403() {
        ResponseEntity<ApiError> response = handler.handleDenied(new AccessDeniedException("denied"), req("/api/billing"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    void handleGeneric_returns500() {
        ResponseEntity<ApiError> response = handler.handleGeneric(new RuntimeException("boom"), req("/api/anything"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getStatus());
        assertNotEquals("boom", response.getBody().getMessage()); // must not leak raw exception text
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }
}
