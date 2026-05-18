package com.example.notificationservice.service.cooldown;

import java.time.Instant;
import java.time.Duration;

public interface NotificationCooldownStore {

    boolean tryAcquire(String dedupeKey, Instant sentAt, Duration cooldown);

    void release(String dedupeKey);
}
