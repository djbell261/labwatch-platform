package com.example.notificationservice.service.channel;

import com.example.notificationservice.dto.AlertEventMessage;

public interface NotificationChannel {

    void dispatch(AlertEventMessage alertEventMessage);

    default boolean dispatchWithResult(AlertEventMessage alertEventMessage) {
        dispatch(alertEventMessage);
        return true;
    }

    String channelName();
}
