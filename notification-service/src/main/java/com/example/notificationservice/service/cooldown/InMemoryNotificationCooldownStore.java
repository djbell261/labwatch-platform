package com.example.notificationservice.service.cooldown;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryNotificationCooldownStore implements NotificationCooldownStore {

    private final ConcurrentMap<String, Instant> sentNotifications = new ConcurrentHashMap<>();

    @Override
    public Instant getLastSentAt(String dedupeKey) {
        return sentNotifications.get(dedupeKey);
    }

    @Override
    public void markSent(String dedupeKey, Instant sentAt) {
        sentNotifications.put(dedupeKey, sentAt);
    }
}
