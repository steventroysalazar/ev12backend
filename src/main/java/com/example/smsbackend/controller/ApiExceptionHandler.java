package com.example.smsbackend.controller;

import com.example.smsbackend.service.GatewayClientException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(GatewayClientException.class)
    public ResponseEntity<Map<String, Object>> handleGateway(GatewayClientException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        return ResponseEntity.status(status).body(Map.of(
            "success", false,
            "error", e.getMessage(),
            "downstreamStatus", e.getStatusCode()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "success", false,
            "error", "Validation failed",
            "details", e.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of("field", err.getField(), "message", err.getDefaultMessage()))
                .toList()
        ));
    }


    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
            "success", false,
            "error", e.getReason() != null ? e.getReason() : "Request failed"
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")
            ? HttpStatus.NOT_FOUND
            : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of(
            "success", false,
            "error", e.getMessage()
        ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(DataAccessException e) {
        LOGGER.error("Database error while handling API request.", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "success", false,
            "error", "Database unavailable",
            "message", "The service cannot access its database right now. Please retry shortly.",
            "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
            "success", false,
            "error", e.getMessage()
        ));
    }
}
