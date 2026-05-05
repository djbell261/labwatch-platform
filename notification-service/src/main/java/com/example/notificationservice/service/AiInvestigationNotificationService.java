package com.example.notificationservice.service;

import com.example.notificationservice.dto.AiInvestigationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiInvestigationNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AiInvestigationNotificationService.class);

    public void logInvestigation(AiInvestigationEvent aiInvestigationEvent) {
        if (aiInvestigationEvent == null) {
            return;
        }

        log.warn(
                "\n[AI INVESTIGATION]\nMachine: {}\nAlert: {} {}\nSummary: {}\nLikely Cause: {}\nRecommended Action: {}\nConfidence: {}\n",
                NotificationDispatchService.fallback(aiInvestigationEvent.getMachineIdentifier()),
                NotificationDispatchService.fallback(aiInvestigationEvent.getAlertType()),
                NotificationDispatchService.fallback(aiInvestigationEvent.getSeverity()),
                NotificationDispatchService.fallback(aiInvestigationEvent.getSummary()),
                NotificationDispatchService.fallback(aiInvestigationEvent.getLikelyCause()),
                NotificationDispatchService.fallback(aiInvestigationEvent.getRecommendedAction()),
                NotificationDispatchService.fallback(aiInvestigationEvent.getConfidence())
        );
    }
}
