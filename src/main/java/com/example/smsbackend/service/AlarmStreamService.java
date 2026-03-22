package com.example.smsbackend.service;

import com.example.smsbackend.dto.AlarmUpdateEventResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AlarmStreamService {

    private static final long EMITTER_TIMEOUT_MS = Duration.ofHours(6).toMillis();
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        subscribers.add(emitter);

        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> {
            subscribers.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> subscribers.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("alarm stream connected"));
        } catch (IOException ignored) {
            subscribers.remove(emitter);
            emitter.completeWithError(ignored);
        }

        return emitter;
    }

    public void publish(AlarmUpdateEventResponse update) {
        subscribers.removeIf(emitter -> !sendUpdate(emitter, update));
    }

    private boolean sendUpdate(SseEmitter emitter, AlarmUpdateEventResponse update) {
        try {
            emitter.send(SseEmitter.event()
                .name("alarm-update")
                .data(update));
            return true;
        } catch (IOException ex) {
            emitter.completeWithError(ex);
            return false;
        }
    }
}
