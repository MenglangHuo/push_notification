package com.onesignal.platform.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.notification.api.dto.NotificationDto;
import com.onesignal.platform.notification.domain.NotificationPriority;
import com.onesignal.platform.notification.domain.TargetType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationServerApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Seeded in V2 migration:
    // os_live_demo1234567890abcdef1234567890 (Acme Retail Store)
    private static final String VALID_REST_KEY = "os_live_demo1234567890abcdef1234567890";

    @Test
    void testCreateAndGetNotificationViaServerApiKeySuccess() throws Exception {
        NotificationDto.CreateNotificationRequest request = NotificationDto.CreateNotificationRequest.builder()
                .title("Weekend Mega Sale!")
                .body("Up to 70% discount on summer collections.")
                .priority(NotificationPriority.HIGH)
                .targetType(TargetType.ALL)
                .deepLink("https://retail.acme.com/summer-sale")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Api-Key", VALID_REST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Weekend Mega Sale!"))
                .andReturn();

        NotificationDto.NotificationResponse created = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                NotificationDto.NotificationResponse.class
        );

        // Fetch via GET /api/v1/notifications/{id}
        mockMvc.perform(get("/api/v1/notifications/" + created.getId())
                        .header("X-Api-Key", VALID_REST_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.title").value("Weekend Mega Sale!"));
    }

    @Test
    void testCreateNotificationWithoutApiKeyForbidden() throws Exception {
        NotificationDto.CreateNotificationRequest request = NotificationDto.CreateNotificationRequest.builder()
                .title("Unauthorized Test")
                .body("Should fail without key")
                .build();

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
