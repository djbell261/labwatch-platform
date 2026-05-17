package com.example.notificationservice.service.channel;

import com.example.notificationservice.config.NotificationProperties;
import com.example.notificationservice.dto.AlertEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.InputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EmailNotificationChannelTest {

    private JavaMailSender mailSender;
    private MailProperties mailProperties;
    private NotificationProperties notificationProperties;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailProperties = new MailProperties();
        notificationProperties = new NotificationProperties();
        notificationProperties.getEmail().setFrom("labwatch@localhost");
        notificationProperties.getEmail().setTo("alerts@example.com");
        mailProperties.setHost("smtp.example.com");
    }

    @Test
    void emailDisabledSkipsSending() {
        notificationProperties.getEmail().setEnabled(false);

        EmailNotificationChannel channel = new EmailNotificationChannel(notificationProperties, mailSender, mailProperties);

        channel.dispatch(alert());

        verifyNoInteractions(mailSender);
    }

    @Test
    void emailEnabledSendsMessage() {
        notificationProperties.getEmail().setEnabled(true);
        CapturingMailSender capturingMailSender = new CapturingMailSender();

        EmailNotificationChannel channel = new EmailNotificationChannel(notificationProperties, capturingMailSender, mailProperties);

        channel.dispatch(alert());

        assertEquals("alerts@example.com", capturingMailSender.message.getTo()[0]);
        assertEquals("labwatch@localhost", capturingMailSender.message.getFrom());
        assertEquals("[HIGH] CPU Alert on derwins-macbook", capturingMailSender.message.getSubject());
        assertEquals(
                String.join(
                        "\n",
                        "LabWatch Alert Notification",
                        "",
                        "Machine: derwins-macbook",
                        "Hostname: Mac",
                        "Alert Type: CPU",
                        "Severity: HIGH",
                        "Status: ACTIVE",
                        "Metric Value: 92.4%",
                        "Created At: 2026-05-05T13:32:00Z",
                        "",
                        "Recommended next step:",
                        "Open LabWatch and investigate this machine."
                ),
                capturingMailSender.message.getText()
        );
    }

    @Test
    void missingRecipientSkipsSendingSafely() {
        notificationProperties.getEmail().setEnabled(true);
        notificationProperties.getEmail().setTo(" ");

        EmailNotificationChannel channel = new EmailNotificationChannel(notificationProperties, mailSender, mailProperties);

        channel.dispatch(alert());

        verifyNoInteractions(mailSender);
    }

    @Test
    void missingSmtpHostSkipsSendingSafely() {
        notificationProperties.getEmail().setEnabled(true);
        mailProperties.setHost("");

        EmailNotificationChannel channel = new EmailNotificationChannel(notificationProperties, mailSender, mailProperties);

        channel.dispatch(alert());

        verifyNoInteractions(mailSender);
    }

    @Test
    void mailSenderExceptionDoesNotCrashDispatch() {
        notificationProperties.getEmail().setEnabled(true);
        doThrow(new MailSendException("boom")).when(mailSender).send(any(SimpleMailMessage.class));

        EmailNotificationChannel channel = new EmailNotificationChannel(notificationProperties, mailSender, mailProperties);

        assertDoesNotThrow(() -> channel.dispatch(alert()));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    private AlertEventMessage alert() {
        return new AlertEventMessage(
                1L,
                "derwins-macbook",
                "Mac",
                "CPU",
                "HIGH",
                "ACTIVE",
                92.4,
                null,
                null,
                null,
                LocalDateTime.of(2026, 5, 5, 13, 32)
        );
    }

    private static final class CapturingMailSender implements JavaMailSender {

        private SimpleMailMessage message;

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            this.message = simpleMessage;
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            this.message = simpleMessages[0];
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage() {
            return null;
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage(InputStream contentStream) {
            return null;
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage mimeMessage) {
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage... mimeMessages) {
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator mimeMessagePreparator) {
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator... mimeMessagePreparators) {
        }
    }
}
