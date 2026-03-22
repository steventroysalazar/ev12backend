package com.example.smsbackend.controller;

import com.example.smsbackend.service.AlarmStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/alarms")
@CrossOrigin(
        originPatterns = {
                "https://ev12frontend-dbdk.vercel.app",
                "https://*.vercel.app",
                "http://localhost:*",
                "https://localhost:*"
        },
        allowCredentials = "true"
)
public class AlarmStreamController {

    private final AlarmStreamService alarmStreamService;

    public AlarmStreamController(AlarmStreamService alarmStreamService) {
        this.alarmStreamService = alarmStreamService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlarmUpdates() {
        return alarmStreamService.subscribe();
    }
}
