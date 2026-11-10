package com.onesignal.platform.realtime.api;

import com.onesignal.platform.realtime.api.dto.InboundMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketInboundControllerTest {

    private WebSocketInboundController controller;
    private Principal principal;

    @BeforeEach
    void setUp() {
        controller = new WebSocketInboundController();
        principal = () -> "test-user-uuid";
    }

    @Test
    void testHandleInboundMessage_PingPong() {
        InboundMessageDto.InboundRequest request = InboundMessageDto.InboundRequest.builder()
                .action("PING")
                .clientTimestamp(System.currentTimeMillis())
                .build();

        InboundMessageDto.InboundResponse response = controller.handleInboundMessage(request, principal);
        assertNotNull(response);
        assertEquals("PONG", response.getAction());
        assertEquals("SUCCESS", response.getStatus());
    }

    @Test
    void testHandleInboundMessage_EventAck() {
        UUID notifId = UUID.randomUUID();
        InboundMessageDto.InboundRequest request = InboundMessageDto.InboundRequest.builder()
                .action("EVENT_ACK")
                .notificationId(notifId)
                .clientTimestamp(System.currentTimeMillis())
                .build();

        InboundMessageDto.InboundResponse response = controller.handleInboundMessage(request, principal);
        assertNotNull(response);
        assertEquals("ACK", response.getAction());
        assertEquals(notifId, response.getNotificationId());
        assertEquals("SUCCESS", response.getStatus());
    }
}
