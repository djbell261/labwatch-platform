package com.example.notificationservice.service.cooldown;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryNotificationCooldownStore implements NotificationCooldownStore {

    private final ConcurrentMap<String, Instant> sentNotifications = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String dedupeKey, Instant sentAt, Duration cooldown) {
        Instant expiresAt = sentNotifications.get(dedupeKey);
        if (expiresAt != null && expiresAt.isAfter(sentAt)) {
            return false;
        }

        sentNotifications.put(dedupeKey, sentAt.plus(cooldown));
        return true;
    }

    @Override
    public void release(String dedupeKey) {
        sentNotifications.remove(dedupeKey);
    }
}
