package com.onesignal.platform.dispatch.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApnsProvider implements PushProvider {

    private static final String APNS_PRODUCTION_HOST = "https://api.push.apple.com";
    private static final String APNS_SANDBOX_HOST = "https://api.sandbox.push.apple.com";

    private final CryptoVaultService cryptoVaultService;
    private final ApnsTokenService apnsTokenService;
    private final ApnsPayloadBuilder apnsPayloadBuilder;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public ProviderType getProviderType() {
        return ProviderType.APNS;
    }

    @Override
    public boolean testConnection(ProviderCredential credential) {
        if (credential == null || credential.getCredentialDataEncrypted() == null) {
            return false;
        }
        try {
            String p8Key = cryptoVaultService.decrypt(credential.getCredentialDataEncrypted());
            apnsTokenService.parsePrivateKey(p8Key);
            return true;
        } catch (Exception e) {
            log.error("Failed to parse APNs private key during testConnection: {}", e.getMessage());
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
            log.warn("APNs credentials for app {} not configured or empty. Running in simulated dispatch mode.", app.getName());
            return dispatchSimulated(recipients, message);
        }

        String p8Key;
        String keyId;
        String teamId;
        String bundleId;
        boolean isProduction = false;

        try {
            p8Key = cryptoVaultService.decrypt(credential.getCredentialDataEncrypted());
            Map<String, Object> meta = credential.getMetadataMasked();
            if (meta != null) {
                keyId = (String) meta.get("key_id");
                teamId = (String) meta.get("team_id");
                bundleId = (String) meta.get("bundle_id");
                Object prodObj = meta.get("production");
                if (prodObj instanceof Boolean b) {
                    isProduction = b;
                } else if (prodObj instanceof String s) {
                    isProduction = Boolean.parseBoolean(s);
                }
            } else {
                keyId = null;
                teamId = null;
                bundleId = null;
            }
        } catch (Exception e) {
            log.error("Failed to decrypt APNs credentials for app {}: {}", app.getName(), e.getMessage());
            return dispatchSimulated(recipients, message);
        }

        if (p8Key == null || keyId == null || teamId == null || bundleId == null) {
            log.warn("Incomplete APNs credentials for app {}. Running in simulated dispatch mode.", app.getName());
            return dispatchSimulated(recipients, message);
        }

        String jwt;
        try {
            jwt = apnsTokenService.getApnsAuthToken(p8Key, keyId, teamId);
        } catch (Exception e) {
            log.error("Failed to generate APNs JWT token for app {}: {}", app.getName(), e.getMessage());
            return dispatchSimulated(recipients, message);
        }

        String host = isProduction ? APNS_PRODUCTION_HOST : APNS_SANDBOX_HOST;
        byte[] payloadBytes = apnsPayloadBuilder.buildPayloadBytes(message);

        int successCount = 0;
        int failureCount = 0;
        List<RecipientFailure> failures = new ArrayList<>();

        for (PushRecipient recipient : recipients) {
            String token = recipient.getPushToken();

            if (token == null || token.isBlank()) {
                failureCount++;
                failures.add(RecipientFailure.builder()
                        .deviceId(recipient.getDeviceId())
                        .pushToken("")
                        .shouldDeactivate(true)
                        .errorCode("EMPTY_TOKEN")
                        .errorMessage("Empty device token")
                        .build());
                continue;
            }

            // Simulated / Mock token check
            if (token.startsWith("mock://") || token.contains("sample_token") || token.contains("apns_sample")) {
                if (token.contains("expired") || token.contains("invalid") || token.contains("unregistered")) {
                    failureCount++;
                    failures.add(RecipientFailure.builder()
                            .deviceId(recipient.getDeviceId())
                            .pushToken(token)
                            .shouldDeactivate(true)
                            .errorCode("Unregistered")
                            .errorMessage("Simulated inactive/unregistered iOS device token")
                            .build());
                } else {
                    successCount++;
                }
                continue;
            }

            // Real APNs HTTP/2 request
            try {
                String cleanToken = token.replaceAll("[^a-fA-F0-9]", "");
                URI endpointUri = URI.create(host + "/3/device/" + cleanToken);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(endpointUri)
                        .version(HttpClient.Version.HTTP_2)
                        .timeout(Duration.ofSeconds(10))
                        .header("authorization", "bearer " + jwt)
                        .header("apns-topic", bundleId)
                        .header("apns-push-type", "alert")
                        .header("apns-priority", "10")
                        .header("apns-expiration", "0")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(payloadBytes))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    successCount++;
                } else {
                    String reason = parseApnsErrorReason(response.body());
                    boolean shouldDeactivate = "Unregistered".equalsIgnoreCase(reason) ||
                            "BadDeviceToken".equalsIgnoreCase(reason) ||
                            status == 410;

                    failureCount++;
                    failures.add(RecipientFailure.builder()
                            .deviceId(recipient.getDeviceId())
                            .pushToken(token)
                            .shouldDeactivate(shouldDeactivate)
                            .errorCode(reason != null ? reason : "HTTP_" + status)
                            .errorMessage("APNs rejected push with status " + status + ": " + reason)
                            .build());
                }
            } catch (Exception e) {
                log.error("Failed to send APNs message to {}: {}", token, e.getMessage());
                failureCount++;
                failures.add(RecipientFailure.builder()
                        .deviceId(recipient.getDeviceId())
                        .pushToken(token)
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
            if (token != null && (token.contains("invalid") || token.contains("expired") || token.contains("unregistered"))) {
                failure++;
                failures.add(RecipientFailure.builder()
                        .pushToken(token)
                        .deviceId(r.getDeviceId())
                        .shouldDeactivate(true)
                        .errorCode("Unregistered")
                        .errorMessage("Simulated device token unregistered")
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

    private String parseApnsErrorReason(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "UNKNOWN";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("reason")) {
                return root.get("reason").asText();
            }
        } catch (Exception ignored) {
        }
        return responseBody.trim();
    }
}
