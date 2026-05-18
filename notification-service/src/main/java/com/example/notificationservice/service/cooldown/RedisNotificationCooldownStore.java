package com.example.notificationservice.service.cooldown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RedisNotificationCooldownStore implements NotificationCooldownStore {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationCooldownStore.class);
    private static final String KEY_PREFIX = "labwatch:notifications:cooldown:";

    private final StringRedisTemplate redisTemplate;

    public RedisNotificationCooldownStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String dedupeKey, Instant sentAt, Duration cooldown) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    KEY_PREFIX + dedupeKey,
                    String.valueOf(sentAt.toEpochMilli()),
                    cooldown
            );
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException exception) {
            log.error(
                    "event=notification_cooldown_redis_failure dedupeKey={} error={}",
                    dedupeKey,
                    exception.getClass().getSimpleName(),
                    exception
            );
            throw exception;
        }
    }

    @Override
    public void release(String dedupeKey) {
        redisTemplate.delete(KEY_PREFIX + dedupeKey);
    }
}
