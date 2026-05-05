package com.example.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class NotificationConfig {

    @Bean
    public Clock notificationClock() {
        return Clock.systemUTC();
    }
}
