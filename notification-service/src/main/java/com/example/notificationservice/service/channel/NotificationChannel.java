package com.example.notificationservice.service.channel;

import com.example.notificationservice.dto.AlertEventMessage;

public interface NotificationChannel {

    void dispatch(AlertEventMessage alertEventMessage);

    String channelName();
}
