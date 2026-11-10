package com.onesignal.platform.dispatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.dispatch.domain.PushNotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApnsPayloadBuilderTest {

    private ApnsPayloadBuilder payloadBuilder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        payloadBuilder = new ApnsPayloadBuilder(objectMapper);
    }

    @Test
    void testBuildPayloadJson_SimpleAlert() throws Exception {
        PushNotificationMessage message = PushNotificationMessage.builder()
                .title("Order Confirmed")
                .body("Your item has shipped via Express Delivery.")
                .deepLink("https://acme.com/orders/123")
                .build();

        String json = payloadBuilder.buildPayloadJson(message);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("aps"));
        JsonNode aps = root.get("aps");
        assertEquals("Order Confirmed", aps.get("alert").get("title").asText());
        assertEquals("Your item has shipped via Express Delivery.", aps.get("alert").get("body").asText());
        assertEquals("default", aps.get("sound").asText());
        assertEquals(1, aps.get("badge").asInt());
        assertEquals("https://acme.com/orders/123", root.get("deep_link").asText());
    }

    @Test
    void testBuildPayloadJson_RichMediaWithMutableContent() throws Exception {
        PushNotificationMessage message = PushNotificationMessage.builder()
                .title("Flash Sale 🌟")
                .body("50% off select sneakers!")
                .imageUrl("https://cdn.acme.com/promo.jpg")
                .data(Map.of("campaign_id", "fall_2026", "discount", 50))
                .build();

        String json = payloadBuilder.buildPayloadJson(message);
        JsonNode root = objectMapper.readTree(json);

        JsonNode aps = root.get("aps");
        assertEquals(1, aps.get("mutable-content").asInt());
        assertEquals("https://cdn.acme.com/promo.jpg", root.get("image_url").asText());
        assertEquals("fall_2026", root.get("campaign_id").asText());
        assertEquals(50, root.get("discount").asInt());
    }
}
