package com.onesignal.platform.realtime.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    private PresenceService presenceService;
    private UUID appId;

    @BeforeEach
    void setUp() {
        presenceService = new PresenceService(redisTemplate);
        appId = UUID.randomUUID();
    }

    @Test
    void testRegisterAndRemoveSession() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        presenceService.registerSession("sess-1", "user-123", appId);
        assertEquals(1, presenceService.getLocalSessionCount(appId));
        verify(setOperations).add("presence:app:" + appId, "user-123");

        presenceService.removeSession("sess-1");
        assertEquals(0, presenceService.getLocalSessionCount(appId));
        verify(setOperations).remove("presence:app:" + appId, "user-123");
    }

    @Test
    void testGetOnlineCount_DelegatesToRedis() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size("presence:app:" + appId)).thenReturn(42L);

        long count = presenceService.getOnlineCount(appId);
        assertEquals(42L, count);
    }

    @Test
    void testIsOnline_LocalAndRedis() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("presence:app:" + appId, "device-999")).thenReturn(true);

        assertTrue(presenceService.isOnline(appId, "device-999"));
        assertFalse(presenceService.isOnline(appId, "non-existent"));
    }
}
