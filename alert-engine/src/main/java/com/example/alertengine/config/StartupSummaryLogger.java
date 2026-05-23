package com.example.alertengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupSummaryLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupSummaryLogger.class);

    @Value("${spring.application.name:alert-engine}")
    private String applicationName;

    @Value("${server.port:8088}")
    private String serverPort;

    @Value("${app.kafka.topic.health-events:health-events}")
    private String healthEventsTopic;

    @Value("${app.kafka.topic.alert-events:alert-events}")
    private String alertEventsTopic;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info(
                "event=startup_summary service={} port={} healthEventsTopic={} alertEventsTopic={}",
                applicationName,
                serverPort,
                healthEventsTopic,
                alertEventsTopic
        );
    }
}
