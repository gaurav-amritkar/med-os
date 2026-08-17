package com.medos.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private Map<String, String> validationErrors;
    private List<String> details;

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(status, error, code, message, path, LocalDateTime.now(), null, null);
    }

    public static ApiError of(int status, String error, String code, String message, String path, Map<String, String> validationErrors) {
        return new ApiError(status, error, code, message, path, LocalDateTime.now(), validationErrors, null);
    }

    public static ApiError of(int status, String error, String code, String message, String path, List<String> details) {
        return new ApiError(status, error, code, message, path, LocalDateTime.now(), null, details);
    }
}
