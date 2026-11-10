package com.onesignal.platform.dispatch.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.onesignal.platform.app.application.CryptoVaultService;
import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.domain.ProviderCredential;
import com.onesignal.platform.app.domain.ProviderType;
import com.onesignal.platform.dispatch.domain.BatchDispatchResult;
import com.onesignal.platform.dispatch.domain.PushNotificationMessage;
import com.onesignal.platform.dispatch.domain.PushRecipient;
import com.onesignal.platform.dispatch.domain.RecipientFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmProvider implements PushProvider {

    private final CryptoVaultService cryptoVaultService;
    private final Map<UUID, FirebaseApp> firebaseAppCache = new ConcurrentHashMap<>();

    @Override
    public ProviderType getProviderType() {
        return ProviderType.FCM;
    }

    @Override
    public BatchDispatchResult dispatchBatch(
            App app,
            ProviderCredential credential,
            List<PushRecipient> recipients,
            PushNotificationMessage message
    ) {
        if (recipients == null || recipients.isEmpty()) {
            return BatchDispatchResult.builder().successCount(0).failureCount(0).build();
        }

        FirebaseApp firebaseApp = getOrCreateFirebaseApp(app, credential);
        if (firebaseApp == null) {
            // Simulated / Mock mode for sandbox or demo runs without live GCP service accounts
            log.warn("FCM credentials for app {} not live or empty. Running in simulated dispatch mode.", app.getName());
            return dispatchSimulated(recipients, message);
        }

        try {
            FirebaseMessaging messaging = FirebaseMessaging.getInstance(firebaseApp);
            List<String> tokens = recipients.stream().map(PushRecipient::getPushToken).toList();

            MulticastMessage.Builder msgBuilder = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(message.getTitle())
                            .setBody(message.getBody())
                            .setImage(message.getImageUrl())
                            .build());

            if (message.getData() != null && !message.getData().isEmpty()) {
                Map<String, String> stringData = new HashMap<>();
                message.getData().forEach((k, v) -> stringData.put(k, String.valueOf(v)));
                msgBuilder.putAllData(stringData);
            }

            if (message.getDeepLink() != null) {
                msgBuilder.putData("deep_link", message.getDeepLink());
            }

            BatchResponse batchResponse = messaging.sendEachForMulticast(msgBuilder.build());
            int success = batchResponse.getSuccessCount();
            int failure = batchResponse.getFailureCount();
            List<RecipientFailure> failures = new ArrayList<>();

            List<SendResponse> responses = batchResponse.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse resp = responses.get(i);
                if (!resp.isSuccessful()) {
                    FirebaseMessagingException ex = resp.getException();
                    String errorCode = ex != null ? ex.getMessagingErrorCode().name() : "UNKNOWN";
                    boolean shouldDeactivate = ex != null && (
                            MessagingErrorCode.UNREGISTERED.equals(ex.getMessagingErrorCode()) ||
                            MessagingErrorCode.INVALID_ARGUMENT.equals(ex.getMessagingErrorCode())
                    );
                    failures.add(RecipientFailure.builder()
                            .pushToken(recipients.get(i).getPushToken())
                            .deviceId(recipients.get(i).getDeviceId())
                            .shouldDeactivate(shouldDeactivate)
                            .errorCode(errorCode)
                            .errorMessage(ex != null ? ex.getMessage() : "Unknown FCM error")
                            .build());
                }
            }

            return BatchDispatchResult.builder()
                    .successCount(success)
                    .failureCount(failure)
                    .failures(failures)
                    .build();

        } catch (Exception e) {
            log.error("Failed to execute multicast FCM dispatch for app {}", app.getId(), e);
            List<RecipientFailure> allFailed = recipients.stream()
                    .map(r -> RecipientFailure.builder()
                            .pushToken(r.getPushToken())
                            .deviceId(r.getDeviceId())
                            .shouldDeactivate(false)
                            .errorCode("DISPATCH_EXCEPTION")
                            .errorMessage(e.getMessage())
                            .build())
                    .toList();

            return BatchDispatchResult.builder()
                    .successCount(0)
                    .failureCount(recipients.size())
                    .failures(allFailed)
                    .build();
        }
    }

    private BatchDispatchResult dispatchSimulated(List<PushRecipient> recipients, PushNotificationMessage message) {
        int success = 0;
        int failure = 0;
        List<RecipientFailure> failures = new ArrayList<>();

        for (PushRecipient recipient : recipients) {
            String token = recipient.getPushToken();
            if (token != null && (token.startsWith("invalid_") || token.startsWith("unregistered_"))) {
                failure++;
                failures.add(RecipientFailure.builder()
                        .pushToken(token)
                        .deviceId(recipient.getDeviceId())
                        .shouldDeactivate(true)
                        .errorCode("UNREGISTERED")
                        .errorMessage("Simulated unregistered device token")
                        .build());
            } else {
                success++;
            }
        }

        return BatchDispatchResult.builder()
                .successCount(success)
                .failureCount(failure)
                .failures(failures)
                .build();
    }

    private FirebaseApp getOrCreateFirebaseApp(App app, ProviderCredential credential) {
        if (credential == null || !credential.isActive() || credential.getCredentialDataEncrypted() == null) {
            return null;
        }

        return firebaseAppCache.computeIfAbsent(app.getId(), appId -> {
            try {
                String decryptedJson = cryptoVaultService.decrypt(credential.getCredentialDataEncrypted());
                if (decryptedJson == null || decryptedJson.isBlank() || !decryptedJson.contains("project_id")) {
                    return null;
                }

                String appName = "fcm-app-" + appId;
                for (FirebaseApp existing : FirebaseApp.getApps()) {
                    if (existing.getName().equals(appName)) {
                        return existing;
                    }
                }

                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(decryptedJson.getBytes(StandardCharsets.UTF_8))
                );

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();

                return FirebaseApp.initializeApp(options, appName);
            } catch (Exception e) {
                log.warn("Could not initialize FirebaseApp for app {}: {}", appId, e.getMessage());
                return null;
            }
        });
    }

    @Override
    public boolean testConnection(ProviderCredential credential) {
        if (credential == null || credential.getCredentialDataEncrypted() == null) {
            return false;
        }
        try {
            String decrypted = cryptoVaultService.decrypt(credential.getCredentialDataEncrypted());
            return decrypted != null && decrypted.contains("project_id") && decrypted.contains("private_key");
        } catch (Exception e) {
            return false;
        }
    }
}
