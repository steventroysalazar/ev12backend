package com.example.smsbackend.controller;

import com.example.smsbackend.dto.AuthResponse;
import com.example.smsbackend.dto.CreateUserRequest;
import com.example.smsbackend.dto.FcmTokenResponse;
import com.example.smsbackend.dto.LoginAuditContext;
import com.example.smsbackend.dto.LoginLogResponse;
import com.example.smsbackend.dto.LoginRequest;
import com.example.smsbackend.dto.UpsertFcmTokenRequest;
import com.example.smsbackend.dto.UserResponse;
import com.example.smsbackend.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        String ipAddress = forwardedFor != null ? forwardedFor.split(",")[0].trim() : servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.login(request, new LoginAuditContext(ipAddress, userAgent)));
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<FcmTokenResponse> upsertFcmToken(@Valid @RequestBody UpsertFcmTokenRequest request) {
        return ResponseEntity.ok(authService.upsertFcmToken(request));
    }

    @GetMapping("/login-logs")
    public ResponseEntity<List<LoginLogResponse>> listLoginLogs(@RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(authService.listLoginLogs(userId));
    }
}
