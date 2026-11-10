package com.onesignal.platform.realtime.api;

import com.onesignal.platform.realtime.api.dto.InboundMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketInboundController {

    @MessageMapping("/inbound")
    @SendToUser("/queue/inbound-ack")
    public InboundMessageDto.InboundResponse handleInboundMessage(
            @Payload InboundMessageDto.InboundRequest request,
            Principal principal
    ) {
        String sender = principal != null ? principal.getName() : "anonymous";
        log.info("Received STOMP inbound message from sender {}: action={}, notificationId={}",
                sender, request.getAction(), request.getNotificationId());

        long now = System.currentTimeMillis();

        if ("PING".equalsIgnoreCase(request.getAction())) {
            return InboundMessageDto.InboundResponse.builder()
                    .action("PONG")
                    .serverTimestamp(now)
                    .status("SUCCESS")
                    .message("Heartbeat acknowledged")
                    .build();
        }

        if ("EVENT_ACK".equalsIgnoreCase(request.getAction())) {
            return InboundMessageDto.InboundResponse.builder()
                    .action("ACK")
                    .notificationId(request.getNotificationId())
                    .serverTimestamp(now)
                    .status("SUCCESS")
                    .message("Notification receipt acknowledged")
                    .details(Map.of("sender", sender))
                    .build();
        }

        // Generic Inbound mobile/web to server message
        return InboundMessageDto.InboundResponse.builder()
                .action("ACK")
                .notificationId(request.getNotificationId())
                .serverTimestamp(now)
                .status("SUCCESS")
                .message("Inbound message processed successfully")
                .details(Map.of("sender", sender, "action", request.getAction() != null ? request.getAction() : "DEFAULT"))
                .build();
    }
}
