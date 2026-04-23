package com.example.smsbackend.service;

import com.example.smsbackend.dto.ErrorLogResponse;
import com.example.smsbackend.entity.ErrorLog;
import com.example.smsbackend.repository.ErrorLogRepository;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ErrorLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorLogService.class);
    private static final int MAX_PATH_LENGTH = 255;
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_STACK_TRACE_LENGTH = 8000;

    private final ErrorLogRepository errorLogRepository;

    public ErrorLogService(ErrorLogRepository errorLogRepository) {
        this.errorLogRepository = errorLogRepository;
    }

    public void logError(Exception exception, int statusCode, String method, String path) {
        try {
            ErrorLog log = new ErrorLog();
            log.setMethod(trimOrDefault(method, 16, "UNKNOWN"));
            log.setPath(trimOrDefault(path, MAX_PATH_LENGTH, "UNKNOWN"));
            log.setStatusCode(statusCode);
            log.setErrorType(trim(exception.getClass().getSimpleName(), 200));
            log.setErrorMessage(trim(exception.getMessage(), MAX_MESSAGE_LENGTH));
            log.setStackTrace(trim(stackTrace(exception), MAX_STACK_TRACE_LENGTH));
            log.setOccurredAt(Instant.now());
            errorLogRepository.save(log);
        } catch (Exception persistenceException) {
            LOGGER.error("Failed to persist API error log.", persistenceException);
        }
    }

    public List<ErrorLogResponse> getRecentErrors(int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 500);
        return errorLogRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, normalizedLimit))
            .stream()
            .map(log -> new ErrorLogResponse(
                log.getId(),
                log.getMethod(),
                log.getPath(),
                log.getStatusCode(),
                log.getErrorType(),
                log.getErrorMessage(),
                log.getStackTrace(),
                log.getOccurredAt()
            ))
            .toList();
    }

    private String stackTrace(Exception exception) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            exception.printStackTrace(pw);
        }
        return sw.toString();
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String trimOrDefault(String value, int maxLength, String fallback) {
        String trimmed = trim(value, maxLength);
        return trimmed == null ? fallback : trimmed;
    }
}
