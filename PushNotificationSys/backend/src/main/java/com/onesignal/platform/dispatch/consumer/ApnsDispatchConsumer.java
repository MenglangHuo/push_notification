package com.onesignal.platform.dispatch.consumer;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.domain.ProviderCredential;
import com.onesignal.platform.app.domain.ProviderType;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.app.infrastructure.ProviderCredentialRepository;
import com.onesignal.platform.device.application.DeviceRegistrationService;
import com.onesignal.platform.dispatch.domain.BatchDispatchResult;
import com.onesignal.platform.dispatch.domain.PushRecipient;
import com.onesignal.platform.dispatch.domain.RecipientFailure;
import com.onesignal.platform.dispatch.event.ApnsDispatchBatchMessage;
import com.onesignal.platform.dispatch.service.ApnsProvider;
import com.onesignal.platform.dispatch.service.NotificationEventProducer;
import com.onesignal.platform.notification.domain.DeliveryLog;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.domain.NotificationStatus;
import com.onesignal.platform.notification.infrastructure.DeliveryLogRepository;
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
public class ApnsDispatchConsumer {

    private final ApnsProvider apnsProvider;
    private final AppRepository appRepository;
    private final ProviderCredentialRepository credentialRepository;
    private final NotificationRepository notificationRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final DeviceRegistrationService deviceRegistrationService;
    private final NotificationEventProducer producer;

    @KafkaListener(
            topics = NotificationEventProducer.TOPIC_DISPATCH_APNS,
            groupId = "apns-dispatch-group"
    )
    @Transactional
    public void onApnsDispatchBatch(ApnsDispatchBatchMessage batchMessage) {
        UUID notificationId = batchMessage.getNotificationId();
        UUID appId = batchMessage.getAppId();
        List<PushRecipient> recipients = batchMessage.getRecipients();

        log.info("Processing APNs dispatch batch for notification: {}, recipients: {}", notificationId, recipients.size());

        try {
            App app = appRepository.findById(appId).orElse(null);
            if (app == null) {
                log.error("App {} not found during APNs dispatch", appId);
                producer.sendToDlq(notificationId.toString(), batchMessage, "App not found");
                return;
            }

            ProviderCredential credential = credentialRepository.findByAppIdAndProviderType(appId, ProviderType.APNS)
                    .orElse(null);

            long startTime = System.currentTimeMillis();
            BatchDispatchResult result = apnsProvider.dispatchBatch(app, credential, recipients, batchMessage.getMessage());
            long latencyMs = System.currentTimeMillis() - startTime;

            // 1. Process failures & token invalidation (410 Unregistered / BadDeviceToken)
            Map<String, RecipientFailure> failureMap = result.getFailures().stream()
                    .collect(Collectors.toMap(RecipientFailure::getPushToken, f -> f, (f1, f2) -> f1));

            List<DeliveryLog> logs = new ArrayList<>();
            for (PushRecipient recipient : recipients) {
                RecipientFailure failure = failureMap.get(recipient.getPushToken());
                if (failure != null) {
                    logs.add(DeliveryLog.builder()
                            .notificationId(notificationId)
                            .deviceId(recipient.getDeviceId())
                            .appId(appId)
                            .provider("APNS")
                            .status("FAILED")
                            .statusCode(failure.getErrorCode())
                            .errorMessage(failure.getErrorMessage())
                            .latencyMs((int) latencyMs)
                            .build());

                    if (failure.isShouldDeactivate()) {
                        deviceRegistrationService.deactivateDevice(
                                appId, recipient.getPushToken(), "APNs error: " + failure.getErrorCode()
                        );
                    }
                } else {
                    logs.add(DeliveryLog.builder()
                            .notificationId(notificationId)
                            .deviceId(recipient.getDeviceId())
                            .appId(appId)
                            .provider("APNS")
                            .status("SUCCESS")
                            .statusCode("200")
                            .latencyMs((int) latencyMs)
                            .build());
                }
            }

            deliveryLogRepository.saveAll(logs);

            // 2. Update notification counters & status
            Optional<Notification> notifOpt = notificationRepository.findById(notificationId);
            if (notifOpt.isPresent()) {
                Notification notif = notifOpt.get();
                notif.setSentCount(notif.getSentCount() + recipients.size());
                notif.setDeliveredCount(notif.getDeliveredCount() + result.getSuccessCount());
                notif.setFailedCount(notif.getFailedCount() + result.getFailureCount());
                notif.setStatus(NotificationStatus.COMPLETED);
                notificationRepository.save(notif);
            }

            log.info("APNs batch dispatched successfully for notification {}: {} succeeded, {} failed (latency: {}ms)",
                    notificationId, result.getSuccessCount(), result.getFailureCount(), latencyMs);

        } catch (Exception e) {
            log.error("Unhandled error dispatching APNs batch for notification {}", notificationId, e);
            producer.sendToDlq(notificationId.toString(), batchMessage, e.getMessage());
        }
    }
}
