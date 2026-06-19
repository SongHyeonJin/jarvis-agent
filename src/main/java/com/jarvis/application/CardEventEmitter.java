package com.jarvis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class CardEventEmitter {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        log.debug("[CardEventEmitter] client subscribed, total={}", emitters.size());
        return emitter;
    }

    public void broadcast(String eventType) {
        send("card-update", eventType);
    }

    public int broadcastWake() {
        int count = emitters.size();
        log.info("[CardEventEmitter] broadcasting wake signal to {} clients", count);
        send("wake", "activate");
        return count;
    }

    private void send(String eventName, String data) {
        if (emitters.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter em : emitters) {
            try {
                em.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                dead.add(em);
            }
        }
        emitters.removeAll(dead);
    }
}
