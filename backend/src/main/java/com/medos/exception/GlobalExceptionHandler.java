package com.medos.exception;

import jakarta.servlet.http.HttpServletRequest;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.warn("Business exception: {}", ex.getMessage());
        String code = ex.getStatus().is4xxClientError() ? "BUSINESS_ERROR" : "SERVER_ERROR";
        ApiError err = ApiError.of(
            ex.getStatus().value(),
            ex.getStatus().getReasonPhrase(),
            code,
            ex.getMessage(),
            req.getRequestURI()
        );
        return ResponseEntity.status(ex.getStatus()).body(err);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        ApiError err = ApiError.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            "NOT_FOUND",
            ex.getMessage(),
            req.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCreds(BadCredentialsException ex, HttpServletRequest req) {
        ApiError err = ApiError.of(
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "UNAUTHORIZED",
            "Invalid credentials",
            req.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        ApiError err = ApiError.of(
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            "FORBIDDEN",
            "Access denied",
            req.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
            errors.put(fe.getField(), fe.getDefaultMessage())
        );
        ApiError err = ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "VALIDATION_ERROR",
            "Invalid request parameters",
            req.getRequestURI(),
            errors
        );
        return ResponseEntity.badRequest().body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        ApiError err = ApiError.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            req.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}
