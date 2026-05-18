package com.example.monitoringapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupSummaryLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupSummaryLogger.class);

    @Value("${spring.application.name:monitoring-api}")
    private String applicationName;

    @Value("${server.port:8089}")
    private String serverPort;

    @Value("${labwatch.auth.enabled:false}")
    private boolean authEnabled;

    @Value("${labwatch.agent-auth.enabled:false}")
    private boolean agentAuthEnabled;

    @Value("${app.kafka.topic.health-events:health-events}")
    private String healthEventsTopic;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info(
                "event=startup_summary service={} port={} authEnabled={} agentAuthEnabled={} healthEventsTopic={}",
                applicationName,
                serverPort,
                authEnabled,
                agentAuthEnabled,
                healthEventsTopic
        );
    }
}
