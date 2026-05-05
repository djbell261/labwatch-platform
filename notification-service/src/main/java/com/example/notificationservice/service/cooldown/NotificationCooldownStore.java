package com.example.notificationservice.service.cooldown;

import java.time.Instant;

public interface NotificationCooldownStore {

    Instant getLastSentAt(String dedupeKey);

    void markSent(String dedupeKey, Instant sentAt);
}
