package com.example.smsbackend.controller;

import com.example.smsbackend.dto.ErrorLogResponse;
import com.example.smsbackend.service.ErrorLogService;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/error-logs")
@CrossOrigin(
    originPatterns = {
        "https://ev12frontend-dbdk.vercel.app",
        "https://*.vercel.app",
        "http://localhost:*",
        "https://localhost:*"
    },
    allowCredentials = "true"
)
public class ErrorLogController {

    private final ErrorLogService errorLogService;

    public ErrorLogController(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @GetMapping
    public List<ErrorLogResponse> getErrorLogs(@RequestParam(defaultValue = "100") int limit) {
        return errorLogService.getRecentErrors(limit);
    }
}
