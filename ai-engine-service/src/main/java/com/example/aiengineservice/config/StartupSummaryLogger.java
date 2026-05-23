package com.example.aiengineservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupSummaryLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupSummaryLogger.class);

    @Value("${spring.application.name:ai-engine-service}")
    private String applicationName;

    @Value("${server.port:8090}")
    private String serverPort;

    @Value("${ai.provider:mock}")
    private String aiProvider;

    @Value("${app.ai-investigation.concurrency:4}")
    private int aiConcurrency;

    @Value("${app.kafka.topic.alert-events:alert-events}")
    private String alertEventsTopic;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info(
                "event=startup_summary service={} port={} aiProvider={} aiInvestigationConcurrency={} alertEventsTopic={}",
                applicationName,
                serverPort,
                aiProvider,
                aiConcurrency,
                alertEventsTopic
        );
    }
}
