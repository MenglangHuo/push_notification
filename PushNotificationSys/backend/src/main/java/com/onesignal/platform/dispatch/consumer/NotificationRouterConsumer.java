package com.onesignal.platform.dispatch.consumer;

import com.onesignal.platform.device.domain.Device;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.dispatch.domain.PushNotificationMessage;
import com.onesignal.platform.dispatch.domain.PushRecipient;
import com.onesignal.platform.dispatch.event.FcmDispatchBatchMessage;
import com.onesignal.platform.dispatch.event.NotificationCreatedEvent;
import com.onesignal.platform.dispatch.service.NotificationEventProducer;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.domain.NotificationStatus;
import com.onesignal.platform.notification.domain.TargetType;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRouterConsumer {

    private static final int BATCH_SIZE = 500;

    private final NotificationRepository notificationRepository;
    private final DeviceRepository deviceRepository;
    private final NotificationEventProducer producer;
    private final com.onesignal.platform.realtime.service.RealtimeNotificationRelayService realtimeRelayService;
    private final com.onesignal.platform.segment.application.SegmentService segmentService;

    @KafkaListener(
            topics = NotificationEventProducer.TOPIC_NOTIFICATION_CREATED,
            groupId = "notification-router-group"
    )
    @Transactional
    public void onNotificationCreated(NotificationCreatedEvent event) {
        log.info("Received NotificationCreatedEvent for notification: {}", event.getNotificationId());

        Optional<Notification> notifOpt = notificationRepository.findById(event.getNotificationId());
        if (notifOpt.isEmpty()) {
            log.warn("Notification not found: {}", event.getNotificationId());
            return;
        }

        Notification notification = notifOpt.get();
        UUID appId = notification.getApp().getId();

        notification.setStatus(NotificationStatus.PROCESSING);
        notificationRepository.save(notification);

        // 1. Resolve recipients based on target type
        List<Device> targetDevices = resolveTargetDevices(notification, appId);
        if (targetDevices.isEmpty()) {
            log.info("No active devices found for notification {}", notification.getId());
            notification.setStatus(NotificationStatus.COMPLETED);
            notificationRepository.save(notification);
            return;
        }

        PushNotificationMessage pushMessage = PushNotificationMessage.builder()
                .notificationId(notification.getId())
                .appId(appId)
                .title(notification.getTitle())
                .body(notification.getBody())
                .imageUrl(notification.getImageUrl())
                .deepLink(notification.getDeepLink())
                .data(notification.getCustomData())
                .priority(notification.getPriority())
                .build();

        // Relay notification to active WebSocket foreground subscribers across Redis Pub/Sub
        realtimeRelayService.publish(com.onesignal.platform.realtime.service.RealtimeNotificationPayload.builder()
                .notificationId(notification.getId())
                .appId(appId)
                .title(notification.getTitle())
                .body(notification.getBody())
                .imageUrl(notification.getImageUrl())
                .deepLink(notification.getDeepLink())
                .customData(notification.getCustomData())
                .priority(notification.getPriority() != null ? notification.getPriority().name() : "NORMAL")
                .timestamp(System.currentTimeMillis())
                .build());

        // 2. Group by platform
        Map<DevicePlatform, List<Device>> byPlatform = targetDevices.stream()
                .collect(Collectors.groupingBy(Device::getPlatform));

        // 3. Dispatch Android devices to FCM worker
        List<Device> androidDevices = byPlatform.getOrDefault(DevicePlatform.ANDROID, Collections.emptyList());
        if (!androidDevices.isEmpty()) {
            List<PushRecipient> androidRecipients = androidDevices.stream()
                    .map(d -> PushRecipient.builder()
                            .deviceId(d.getId())
                            .platform(DevicePlatform.ANDROID)
                            .pushToken(d.getPushToken())
                            .build())
                    .toList();

            for (int i = 0; i < androidRecipients.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, androidRecipients.size());
                List<PushRecipient> chunk = androidRecipients.subList(i, end);

                FcmDispatchBatchMessage batchMessage = FcmDispatchBatchMessage.builder()
                        .notificationId(notification.getId())
                        .appId(appId)
                        .message(pushMessage)
                        .recipients(chunk)
                        .build();

                producer.sendFcmDispatchBatch(batchMessage);
            }
        }

        // 4. Dispatch Web devices to WebPush worker
        List<Device> webDevices = byPlatform.getOrDefault(DevicePlatform.WEB, Collections.emptyList());
        if (!webDevices.isEmpty()) {
            List<PushRecipient> webRecipients = webDevices.stream()
                    .map(d -> PushRecipient.builder()
                            .deviceId(d.getId())
                            .platform(DevicePlatform.WEB)
                            .pushToken(d.getPushToken())
                            .webAuthKey(d.getWebAuthKey())
                            .webP256dhKey(d.getWebP256dhKey())
                            .build())
                    .toList();

            for (int i = 0; i < webRecipients.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, webRecipients.size());
                List<PushRecipient> chunk = webRecipients.subList(i, end);

                com.onesignal.platform.dispatch.event.WebPushDispatchBatchMessage batchMessage =
                        com.onesignal.platform.dispatch.event.WebPushDispatchBatchMessage.builder()
                                .notificationId(notification.getId())
                                .appId(appId)
                                .message(pushMessage)
                                .recipients(chunk)
                                .build();

                producer.sendWebPushDispatchBatch(batchMessage);
            }
        }

        // 5. Dispatch iOS devices to APNs worker
        List<Device> iosDevices = byPlatform.getOrDefault(DevicePlatform.IOS, Collections.emptyList());
        if (!iosDevices.isEmpty()) {
            List<PushRecipient> iosRecipients = iosDevices.stream()
                    .map(d -> PushRecipient.builder()
                            .deviceId(d.getId())
                            .platform(DevicePlatform.IOS)
                            .pushToken(d.getPushToken())
                            .build())
                    .toList();

            for (int i = 0; i < iosRecipients.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, iosRecipients.size());
                List<PushRecipient> chunk = iosRecipients.subList(i, end);

                com.onesignal.platform.dispatch.event.ApnsDispatchBatchMessage batchMessage =
                        com.onesignal.platform.dispatch.event.ApnsDispatchBatchMessage.builder()
                                .notificationId(notification.getId())
                                .appId(appId)
                                .message(pushMessage)
                                .recipients(chunk)
                                .build();

                producer.sendApnsDispatchBatch(batchMessage);
            }
        }
    }

    private List<Device> resolveTargetDevices(Notification notification, UUID appId) {
        TargetType targetType = notification.getTargetType();
        Map<String, Object> payload = notification.getTargetPayload();

        if (targetType == TargetType.ALL) {
            return deviceRepository.findByAppIdAndActiveTrue(appId);
        } else if (targetType == TargetType.SEGMENT && payload != null) {
            Object segIdObj = payload.get("segmentId");
            if (segIdObj != null) {
                try {
                    UUID segmentId = UUID.fromString(segIdObj.toString());
                    return segmentService.resolveDevicesForSegment(appId, segmentId);
                } catch (Exception e) {
                    log.warn("Failed to resolve devices for segment {}: {}", segIdObj, e.getMessage());
                }
            }
        } else if (targetType == TargetType.DEVICE_IDS && payload != null) {
            Object idsObj = payload.get("deviceIds");
            if (idsObj instanceof List<?> rawList) {
                List<UUID> uuids = rawList.stream()
                        .map(Object::toString)
                        .map(UUID::fromString)
                        .toList();
                return deviceRepository.findByAppIdAndIdInAndActiveTrue(appId, uuids);
            }
        } else if (targetType == TargetType.EXTERNAL_USER_IDS && payload != null) {
            Object userIdsObj = payload.get("externalUserIds");
            if (userIdsObj instanceof List<?> rawList) {
                List<String> userIds = rawList.stream().map(Object::toString).toList();
                return deviceRepository.findByAppIdAndExternalUserIdInAndActiveTrue(appId, userIds);
            }
        }

        return deviceRepository.findByAppIdAndActiveTrue(appId);
    }
}
