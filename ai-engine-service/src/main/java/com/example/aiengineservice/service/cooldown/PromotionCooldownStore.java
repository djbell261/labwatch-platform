package com.example.aiengineservice.service.cooldown;

import java.time.Duration;
import java.time.Instant;

public interface PromotionCooldownStore {

    boolean tryAcquire(String key, Instant now, Duration cooldown);

    void release(String key);
}
