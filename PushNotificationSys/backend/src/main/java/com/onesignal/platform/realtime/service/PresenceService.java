package com.onesignal.platform.realtime.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String PRESENCE_REDIS_KEY_PREFIX = "presence:app:";

    private final StringRedisTemplate redisTemplate;

    @Data
    @Builder
    @AllArgsConstructor
    public static class SessionInfo {
        private String sessionId;
        private String principalName;
        private UUID appId;
        private long connectedAt;
    }

    private final Map<String, SessionInfo> localSessions = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, String principalName, UUID appId) {
        SessionInfo info = SessionInfo.builder()
                .sessionId(sessionId)
                .principalName(principalName)
                .appId(appId)
                .connectedAt(System.currentTimeMillis())
                .build();

        localSessions.put(sessionId, info);

        if (appId != null && principalName != null) {
            try {
                redisTemplate.opsForSet().add(PRESENCE_REDIS_KEY_PREFIX + appId, principalName);
            } catch (Exception e) {
                log.warn("Failed to update Redis presence set on connect: {}", e.getMessage());
            }
        }

        log.info("WebSocket session registered: sessionId={}, principal={}, app={}", sessionId, principalName, appId);
    }

    public void removeSession(String sessionId) {
        SessionInfo info = localSessions.remove(sessionId);
        if (info != null && info.getAppId() != null && info.getPrincipalName() != null) {
            try {
                redisTemplate.opsForSet().remove(PRESENCE_REDIS_KEY_PREFIX + info.getAppId(), info.getPrincipalName());
            } catch (Exception e) {
                log.warn("Failed to update Redis presence set on disconnect: {}", e.getMessage());
            }
            log.info("WebSocket session removed: sessionId={}, principal={}, app={}", sessionId, info.getPrincipalName(), info.getAppId());
        }
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = accessor.getUser();
        String principalName = principal != null ? principal.getName() : "anonymous";

        UUID appId = null;
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null && sessionAttributes.containsKey("appId")) {
            Object aId = sessionAttributes.get("appId");
            if (aId instanceof UUID u) {
                appId = u;
            } else if (aId instanceof String s) {
                try {
                    appId = UUID.fromString(s);
                } catch (Exception ignored) {}
            }
        }

        registerSession(sessionId, principalName, appId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        removeSession(sessionId);
    }

    public long getOnlineCount(UUID appId) {
        if (appId == null) {
            return localSessions.size();
        }
        try {
            Long count = redisTemplate.opsForSet().size(PRESENCE_REDIS_KEY_PREFIX + appId);
            return count != null ? count : getLocalSessionCount(appId);
        } catch (Exception e) {
            return getLocalSessionCount(appId);
        }
    }

    public long getLocalSessionCount(UUID appId) {
        if (appId == null) {
            return localSessions.size();
        }
        return localSessions.values().stream()
                .filter(s -> appId.equals(s.getAppId()))
                .count();
    }

    public boolean isOnline(UUID appId, String principalName) {
        if (principalName == null) return false;
        if (appId != null) {
            try {
                Boolean member = redisTemplate.opsForSet().isMember(PRESENCE_REDIS_KEY_PREFIX + appId, principalName);
                if (Boolean.TRUE.equals(member)) return true;
            } catch (Exception ignored) {}
        }
        return localSessions.values().stream()
                .anyMatch(s -> principalName.equals(s.getPrincipalName()));
    }
}
