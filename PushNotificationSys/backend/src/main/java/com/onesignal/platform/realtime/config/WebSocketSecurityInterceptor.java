package com.onesignal.platform.realtime.config;

import com.onesignal.platform.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    public record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String principalName = null;

            // 1. Check Bearer JWT token
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (jwtTokenProvider.validateToken(token)) {
                    UUID userId = jwtTokenProvider.getUserIdFromToken(token);
                    principalName = userId.toString();
                    log.info("STOMP authenticated via JWT for user: {}", principalName);
                }
            }

            // 2. Check X-Device-Token header (mobile/web client)
            if (principalName == null) {
                String deviceToken = accessor.getFirstNativeHeader("X-Device-Token");
                if (deviceToken != null && !deviceToken.isBlank()) {
                    principalName = deviceToken.trim();
                    log.info("STOMP authenticated via device token: {}", principalName);
                }
            }

            // 3. Fallback to guest principal
            if (principalName == null) {
                principalName = "guest_" + accessor.getSessionId();
            }

            accessor.setUser(new StompPrincipal(principalName));

            // Extract X-App-Id if present
            String appIdHeader = accessor.getFirstNativeHeader("X-App-Id");
            if (appIdHeader != null && !appIdHeader.isBlank()) {
                try {
                    UUID appId = UUID.fromString(appIdHeader.trim());
                    if (accessor.getSessionAttributes() != null) {
                        accessor.getSessionAttributes().put("appId", appId);
                    }
                } catch (Exception ignored) {}
            }
        }
        return message;
    }
}
