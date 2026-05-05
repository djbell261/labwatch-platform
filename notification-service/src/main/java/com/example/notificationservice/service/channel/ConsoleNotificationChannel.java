package com.example.notificationservice.service.channel;

import com.example.notificationservice.dto.AlertEventMessage;
import com.example.notificationservice.service.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsoleNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotificationChannel.class);

    @Override
    public void dispatch(AlertEventMessage alertEventMessage) {
        log.warn(
                "\n[{} ALERT]\nMachine: {}\nType: {}\nValue: {}\nTime: {}\n",
                alertEventMessage.getSeverity(),
                NotificationDispatchService.fallback(alertEventMessage.getMachineIdentifier()),
                NotificationDispatchService.fallback(alertEventMessage.getAlertType()),
                NotificationDispatchService.formatMetricValue(alertEventMessage),
                NotificationDispatchService.formatAlertTime(alertEventMessage)
        );
    }

    @Override
    public String channelName() {
        return "ConsoleNotificationChannel";
    }
}
