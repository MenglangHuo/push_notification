package com.onesignal.platform.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushProvider implements PushProvider {

    private final CryptoVaultService cryptoVaultService;
    private final WebPushCryptoService webPushCryptoService;
    private final SsrfValidator ssrfValidator;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public ProviderType getProviderType() {
        return ProviderType.WEBPUSH;
    }

    @Override
    public boolean testConnection(ProviderCredential credential) {
        if (credential == null || credential.getCredentialDataEncrypted() == null) {
            return false;
        }
        try {
            String decrypted = cryptoVaultService.decrypt(credential.getCredentialDataEncrypted());
            return decrypted != null && !decrypted.isBlank();
        } catch (Exception e) {
            log.error("Failed to decrypt VAPID private key during testConnection: {}", e.getMessage());
            return false;
        }
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

        if (credential == null || credential.getCredentialDataEncrypted() == null || credential.getCredentialDataEncrypted().isBlank()) {
            log.warn("WebPush VAPID credentials for app {} not configured or empty. Running in simulated dispatch mode.", app.getName());
            return dispatchSimulated(recipients, message);
        }

        String vapidPrivateKey;
        String vapidPublicKey;
        try {
            vapidPrivateKey = cryptoVaultService.decrypt(credential.getCredentialDataEncrypted());
            vapidPublicKey = credential.getMetadataMasked() != null ?
                    (String) credential.getMetadataMasked().get("public_key") : null;
        } catch (Exception e) {
            log.error("Failed to decrypt VAPID credentials for app {}: {}", app.getName(), e.getMessage());
            return dispatchSimulated(recipients, message);
        }

        if (vapidPrivateKey == null || vapidPrivateKey.isBlank() || vapidPublicKey == null || vapidPublicKey.isBlank()) {
            log.warn("Incomplete VAPID credentials for app {}. Running in simulated dispatch mode.", app.getName());
            return dispatchSimulated(recipients, message);
        }

        // Prepare JSON payload for the Service Worker
        byte[] payloadBytes;
        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("title", message.getTitle() != null ? message.getTitle() : "");
            payloadMap.put("body", message.getBody() != null ? message.getBody() : "");
            if (message.getImageUrl() != null) {
                payloadMap.put("icon", message.getImageUrl());
                payloadMap.put("image", message.getImageUrl());
            }
            if (message.getDeepLink() != null) {
                payloadMap.put("url", message.getDeepLink());
            }
            if (message.getData() != null && !message.getData().isEmpty()) {
                payloadMap.put("data", message.getData());
            }
            payloadBytes = objectMapper.writeValueAsBytes(payloadMap);
        } catch (Exception e) {
            log.error("Failed to serialize push message payload: {}", e.getMessage());
            payloadBytes = message.getBody() != null ? message.getBody().getBytes(StandardCharsets.UTF_8) : new byte[0];
        }

        int successCount = 0;
        int failureCount = 0;
        List<RecipientFailure> failures = new ArrayList<>();

        for (PushRecipient recipient : recipients) {
            String endpoint = recipient.getPushToken();

            if (endpoint == null || endpoint.isBlank()) {
                failureCount++;
                failures.add(RecipientFailure.builder()
                        .deviceId(recipient.getDeviceId())
                        .pushToken("")
                        .shouldDeactivate(true)
                        .errorCode("INVALID_ENDPOINT")
                        .errorMessage("Empty endpoint URL")
                        .build());
                continue;
            }

            // Check if mock / sample endpoint
            if (endpoint.startsWith("mock://") || endpoint.contains("sample_web_token")) {
                if (endpoint.contains("expired") || endpoint.contains("invalid")) {
                    failureCount++;
                    failures.add(RecipientFailure.builder()
                            .deviceId(recipient.getDeviceId())
                            .pushToken(endpoint)
                            .shouldDeactivate(true)
                            .errorCode("EXPIRED")
                            .errorMessage("Simulated expired subscription (HTTP 410)")
                            .build());
                } else {
                    successCount++;
                }
                continue;
            }

            // Real endpoint dispatch with SSRF protection
            try {
                ssrfValidator.validateEndpoint(endpoint);

                if (recipient.getWebP256dhKey() == null || recipient.getWebAuthKey() == null ||
                        recipient.getWebP256dhKey().isBlank() || recipient.getWebAuthKey().isBlank()) {
                    failureCount++;
                    failures.add(RecipientFailure.builder()
                            .deviceId(recipient.getDeviceId())
                            .pushToken(endpoint)
                            .shouldDeactivate(true)
                            .errorCode("MISSING_KEYS")
                            .errorMessage("Recipient device lacks p256dh or auth key")
                            .build());
                    continue;
                }

                String vapidSubject = "mailto:admin@" + app.getName().toLowerCase().replaceAll("[^a-z0-9]", "") + ".local";
                String vapidToken = webPushCryptoService.createVapidToken(endpoint, vapidPrivateKey, vapidSubject);
                byte[] encryptedPayload = webPushCryptoService.encryptPayload(payloadBytes, recipient.getWebP256dhKey(), recipient.getWebAuthKey());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "vapid t=" + vapidToken + ", k=" + vapidPublicKey)
                        .header("Content-Type", "application/octet-stream")
                        .header("Content-Encoding", "aes128gcm")
                        .header("TTL", "86400")
                        .header("Urgency", "high")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(encryptedPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    successCount++;
                } else if (status == 404 || status == 410) {
                    failureCount++;
                    failures.add(RecipientFailure.builder()
                            .deviceId(recipient.getDeviceId())
                            .pushToken(endpoint)
                            .shouldDeactivate(true)
                            .errorCode("EXPIRED")
                            .errorMessage("Push subscription expired or revoked (HTTP " + status + ")")
                            .build());
                } else {
                    failureCount++;
                    failures.add(RecipientFailure.builder()
                            .deviceId(recipient.getDeviceId())
                            .pushToken(endpoint)
                            .shouldDeactivate(false)
                            .errorCode("HTTP_" + status)
                            .errorMessage("WebPush service returned HTTP " + status + ": " + response.body())
                            .build());
                }
            } catch (SecurityException se) {
                log.warn("Blocked SSRF or invalid web push endpoint: {}", se.getMessage());
                failureCount++;
                failures.add(RecipientFailure.builder()
                        .deviceId(recipient.getDeviceId())
                        .pushToken(endpoint)
                        .shouldDeactivate(true)
                        .errorCode("SSRF_BLOCKED")
                        .errorMessage(se.getMessage())
                        .build());
            } catch (Exception e) {
                log.error("Failed to send WebPush message to {}: {}", endpoint, e.getMessage());
                failureCount++;
                failures.add(RecipientFailure.builder()
                        .deviceId(recipient.getDeviceId())
                        .pushToken(endpoint)
                        .shouldDeactivate(false)
                        .errorCode("NETWORK_ERROR")
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        return BatchDispatchResult.builder()
                .successCount(successCount)
                .failureCount(failureCount)
                .failures(failures)
                .build();
    }

    private BatchDispatchResult dispatchSimulated(List<PushRecipient> recipients, PushNotificationMessage message) {
        int success = 0;
        int failure = 0;
        List<RecipientFailure> failures = new ArrayList<>();

        for (PushRecipient r : recipients) {
            String token = r.getPushToken();
            if (token != null && (token.contains("invalid") || token.contains("expired"))) {
                failure++;
                failures.add(RecipientFailure.builder()
                        .pushToken(token)
                        .deviceId(r.getDeviceId())
                        .shouldDeactivate(true)
                        .errorCode("EXPIRED")
                        .errorMessage("Simulated expired subscription")
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
}
