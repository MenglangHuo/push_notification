package com.onesignal.platform.dispatch.service;

import com.onesignal.platform.dispatch.event.FcmDispatchBatchMessage;
import com.onesignal.platform.dispatch.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventProducer {

    public static final String TOPIC_NOTIFICATION_CREATED = "notification.created";
    public static final String TOPIC_DISPATCH_FCM = "dispatch.fcm";
    public static final String TOPIC_DISPATCH_APNS = "dispatch.apns";
    public static final String TOPIC_DISPATCH_WEBPUSH = "dispatch.webpush";
    public static final String TOPIC_DISPATCH_DLQ = "dispatch.dlq";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendNotificationCreated(NotificationCreatedEvent event) {
        log.info("Publishing NotificationCreatedEvent for notification {} (app {})", event.getNotificationId(), event.getAppId());
        kafkaTemplate.send(TOPIC_NOTIFICATION_CREATED, event.getNotificationId().toString(), event);
    }

    public void sendFcmDispatchBatch(FcmDispatchBatchMessage batchMessage) {
        log.info("Publishing FcmDispatchBatchMessage for notification {} ({} recipients)",
                batchMessage.getNotificationId(), batchMessage.getRecipients().size());
        kafkaTemplate.send(TOPIC_DISPATCH_FCM, batchMessage.getNotificationId().toString(), batchMessage);
    }

    public void sendApnsDispatchBatch(com.onesignal.platform.dispatch.event.ApnsDispatchBatchMessage batchMessage) {
        log.info("Publishing ApnsDispatchBatchMessage for notification {} ({} recipients)",
                batchMessage.getNotificationId(), batchMessage.getRecipients().size());
        kafkaTemplate.send(TOPIC_DISPATCH_APNS, batchMessage.getNotificationId().toString(), batchMessage);
    }

    public void sendWebPushDispatchBatch(com.onesignal.platform.dispatch.event.WebPushDispatchBatchMessage batchMessage) {
        log.info("Publishing WebPushDispatchBatchMessage for notification {} ({} recipients)",
                batchMessage.getNotificationId(), batchMessage.getRecipients().size());
        kafkaTemplate.send(TOPIC_DISPATCH_WEBPUSH, batchMessage.getNotificationId().toString(), batchMessage);
    }

    public void sendToDlq(String key, Object payload, String errorReason) {
        log.error("Sending message to DLQ (key: {}, reason: {})", key, errorReason);
        kafkaTemplate.send(TOPIC_DISPATCH_DLQ, key, payload);
    }
}
