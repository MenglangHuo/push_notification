package com.onesignal.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.common.security.Argon2PasswordService;
import com.onesignal.platform.device.api.dto.DeviceDto;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.event.api.dto.SdkEventDto;
import com.onesignal.platform.event.domain.ClientEventType;
import com.onesignal.platform.iam.api.dto.AuthDto;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import com.onesignal.platform.notification.api.dto.NotificationDto;
import com.onesignal.platform.notification.domain.NotificationPriority;
import com.onesignal.platform.notification.domain.TargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformEndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Argon2PasswordService passwordEncoder;

    private String accessToken;
    private final UUID seededAppId = UUID.fromString("11111111-2222-3333-4444-555555555551");
    private final String seededRestApiKey = "os_live_demo1234567890abcdef1234567890";
    private final String seededSdkKey = "os_pub_demo9876543210abcdef9876543210";

    @BeforeEach
    void setUp() throws Exception {
        User admin = userRepository.findByEmail("admin@pushnotify.local").orElse(null);
        if (admin != null) {
            admin.setPasswordHash(passwordEncoder.encode("Password123!"));
            userRepository.save(admin);
        }

        AuthDto.LoginRequest loginReq = new AuthDto.LoginRequest();
        loginReq.setEmail("admin@pushnotify.local");
        loginReq.setPassword("Password123!");

        MvcResult loginResult = mockMvc.perform(post("/admin/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        this.accessToken = loginNode.get("accessToken").asText();
    }

    @Test
    @DisplayName("Complete End-to-End lifecycle: Device Registration -> Push Dispatch -> Event Ingest -> Analytics -> Logs Export")
    void testFullPlatformLifecycle() throws Exception {
        // 1. Device Registration via SDK Key
        String deviceToken = "e2e_fcm_token_" + UUID.randomUUID();
        DeviceDto.RegisterDeviceRequest regRequest = DeviceDto.RegisterDeviceRequest.builder()
                .pushToken(deviceToken)
                .platform(DevicePlatform.ANDROID)
                .externalUserId("user-e2e-101")
                .timezone("America/New_York")
                .language("en")
                .deviceModel("Pixel 8 Pro")
                .tags(Map.of("tier", "gold", "beta", "true"))
                .build();

        MvcResult devResult = mockMvc.perform(post("/sdk/v1/devices")
                        .header("X-Sdk-Key", seededSdkKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalUserId").value("user-e2e-101"))
                .andExpect(jsonPath("$.platform").value("ANDROID"))
                .andReturn();

        JsonNode devNode = objectMapper.readTree(devResult.getResponse().getContentAsString());
        UUID registeredDeviceId = UUID.fromString(devNode.get("id").asText());
        assertThat(registeredDeviceId).isNotNull();

        // 2. Dispatch Notification via Server REST API Key
        NotificationDto.CreateNotificationRequest notifReq = NotificationDto.CreateNotificationRequest.builder()
                .title("E2E Flash Sale")
                .body("Get 50% off right now!")
                .priority(NotificationPriority.HIGH)
                .targetType(TargetType.ALL)
                .customData(Map.of("promo_id", "promo_50off"))
                .build();

        MvcResult notifResult = mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Api-Key", seededRestApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notifReq)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.title").value("E2E Flash Sale"))
                .andReturn();

        JsonNode notifNode = objectMapper.readTree(notifResult.getResponse().getContentAsString());
        UUID createdNotificationId = UUID.fromString(notifNode.get("id").asText());
        assertThat(createdNotificationId).isNotNull();

        // 3. Client Event Telemetry Ingestion via SDK Key
        SdkEventDto.EventItem deliveredEvent = SdkEventDto.EventItem.builder()
                .notificationId(createdNotificationId)
                .deviceId(registeredDeviceId)
                .eventType(ClientEventType.DELIVERED)
                .timestamp(Instant.now())
                .build();

        SdkEventDto.EventItem openedEvent = SdkEventDto.EventItem.builder()
                .notificationId(createdNotificationId)
                .deviceId(registeredDeviceId)
                .eventType(ClientEventType.OPENED)
                .timestamp(Instant.now())
                .build();

        SdkEventDto.IngestEventsRequest ingestReq = SdkEventDto.IngestEventsRequest.builder()
                .events(List.of(deliveredEvent, openedEvent))
                .build();

        mockMvc.perform(post("/sdk/v1/events")
                        .header("X-Sdk-Key", seededSdkKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ingestReq)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedCount").value(2))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        // 4. Query Analytics Overview via Admin API with JWT Bearer
        mockMvc.perform(get("/admin/api/v1/apps/" + seededAppId + "/analytics/overview?range=7d")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeDevices").isNumber())
                .andExpect(jsonPath("$.deliveryRate").isNumber())
                .andExpect(jsonPath("$.openRate").isNumber());

        // 5. Query Analytics Time-Series
        mockMvc.perform(get("/admin/api/v1/apps/" + seededAppId + "/analytics/time-series?range=7d")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").isArray());

        // 6. Query Delivery Logs with Filter
        mockMvc.perform(get("/admin/api/v1/apps/" + seededAppId + "/logs/delivery")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // 7. Export Delivery Logs CSV
        MvcResult csvResult = mockMvc.perform(get("/admin/api/v1/apps/" + seededAppId + "/logs/delivery/export")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        String csvBody = csvResult.getResponse().getContentAsString();
        assertThat(csvBody).contains("id,notification_id,device_id,provider,status");

        // 8. Actuator Health & Metrics
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());

        // 9. OpenAPI 3.0 Documentation
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.1"));
    }
}
