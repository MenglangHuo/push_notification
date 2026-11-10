package com.onesignal.platform.device.application;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.device.api.dto.DeviceDto;
import com.onesignal.platform.device.domain.Device;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.device.infrastructure.DeviceTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceRegistrationServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceTagRepository deviceTagRepository;

    @Mock
    private AppRepository appRepository;

    @InjectMocks
    private DeviceRegistrationService deviceRegistrationService;

    private UUID appId;
    private App app;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        app = App.builder().name("Test App").build();
        app.setId(appId);
    }

    @Test
    void testRegisterNewDeviceSuccess() {
        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(deviceRepository.findByAppIdAndPushToken(eq(appId), anyString())).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device d = invocation.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DeviceDto.RegisterDeviceRequest request = DeviceDto.RegisterDeviceRequest.builder()
                .platform(DevicePlatform.ANDROID)
                .pushToken("fcm_token_test_123456789")
                .externalUserId("user_alice")
                .deviceModel("Pixel 8")
                .language("en")
                .tags(Map.of("role", "customer"))
                .build();

        DeviceDto.DeviceResponse response = deviceRegistrationService.registerDevice(appId, request);

        assertNotNull(response);
        assertEquals(DevicePlatform.ANDROID, response.getPlatform());
        assertEquals("user_alice", response.getExternalUserId());
        assertTrue(response.isActive());
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    void testRegisterExistingDeviceReactivatesAndUpdatesTags() {
        Device existing = Device.builder()
                .id(UUID.randomUUID())
                .app(app)
                .platform(DevicePlatform.ANDROID)
                .pushToken("fcm_token_existing_123")
                .active(false)
                .tags(new ArrayList<>())
                .build();

        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(deviceRepository.findByAppIdAndPushToken(appId, "fcm_token_existing_123")).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenReturn(existing);

        DeviceDto.RegisterDeviceRequest request = DeviceDto.RegisterDeviceRequest.builder()
                .platform(DevicePlatform.ANDROID)
                .pushToken("fcm_token_existing_123")
                .externalUserId("updated_user")
                .tags(Map.of("vip", "true"))
                .build();

        DeviceDto.DeviceResponse response = deviceRegistrationService.registerDevice(appId, request);

        assertNotNull(response);
        assertTrue(existing.isActive());
        assertEquals("updated_user", existing.getExternalUserId());
        assertEquals(1, existing.getTags().size());
    }

    @Test
    void testDeactivateDeviceCallsRepository() {
        deviceRegistrationService.deactivateDevice(appId, "token_to_deactivate", "UNREGISTERED");
        verify(deviceRepository).deactivateToken(appId, "token_to_deactivate");
    }

    @Test
    void testMaskTokenUtility() {
        assertEquals("fcm_to...7890", DeviceRegistrationService.maskToken("fcm_token_1234567890"));
        assertEquals("******", DeviceRegistrationService.maskToken("short"));
        assertEquals("******", DeviceRegistrationService.maskToken(null));
    }
}
