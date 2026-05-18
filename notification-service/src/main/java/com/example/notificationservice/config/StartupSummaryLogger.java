package com.example.notificationservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupSummaryLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupSummaryLogger.class);

    @Value("${spring.application.name:notification-service}")
    private String applicationName;

    @Value("${server.port:8091}")
    private String serverPort;

    @Value("${labwatch.notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${labwatch.notifications.cooldown-seconds:300}")
    private long cooldownSeconds;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info(
                "event=startup_summary service={} port={} emailEnabled={} cooldownSeconds={}",
                applicationName,
                serverPort,
                emailEnabled,
                cooldownSeconds
        );
    }
}
