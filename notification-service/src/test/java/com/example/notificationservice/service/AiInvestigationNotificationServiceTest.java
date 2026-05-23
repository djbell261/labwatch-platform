package com.example.notificationservice.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.notificationservice.dto.AiInvestigationEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiInvestigationNotificationServiceTest {

    @Test
    void logsAiInvestigationToConsole() {
        AiInvestigationNotificationService service = new AiInvestigationNotificationService();
        Logger logger = (Logger) LoggerFactory.getLogger(AiInvestigationNotificationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            service.logInvestigation(new AiInvestigationEvent(
                    UUID.randomUUID(),
                    42L,
                    "derwins-macbook",
                    "MEMORY",
                    "HIGH",
                    "Memory usage crossed the HIGH threshold.",
                    "Several browser/process workloads may be consuming memory.",
                    "- Browser memory spike observed",
                    "- Several tabs are likely contributing",
                    "- Check Chrome Task Manager",
                    "Review top processes and close unnecessary high-memory apps.",
                    "Elevated urgency",
                    "Memory still appears persistent",
                    "- Watch memory over the next few samples",
                    "MEDIUM",
                    LocalDateTime.of(2026, 5, 5, 13, 32)
            ));
        } finally {
            logger.detachAppender(appender);
        }

        assertTrue(appender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("[AI INVESTIGATION]")
                        && event.getFormattedMessage().contains("Memory usage crossed the HIGH threshold.")));
    }
}
