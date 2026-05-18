package com.example.aiengineservice.service.cooldown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RedisPromotionCooldownStore implements PromotionCooldownStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPromotionCooldownStore.class);
    private static final String KEY_PREFIX = "labwatch:anomaly-promotion:cooldown:";

    private final StringRedisTemplate redisTemplate;

    public RedisPromotionCooldownStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String key, Instant now, Duration cooldown) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    KEY_PREFIX + key,
                    String.valueOf(now.toEpochMilli()),
                    cooldown
            );
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException exception) {
            log.error(
                    "event=promotion_cooldown_redis_failure key={} error={}",
                    key,
                    exception.getClass().getSimpleName(),
                    exception
            );
            throw exception;
        }
    }

    @Override
    public void release(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}
