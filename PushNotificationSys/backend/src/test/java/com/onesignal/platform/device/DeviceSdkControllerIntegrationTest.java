package com.onesignal.platform.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.device.api.dto.DeviceDto;
import com.onesignal.platform.device.domain.DevicePlatform;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DeviceSdkControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Seeded in V2 migration:
    // os_pub_demo9876543210abcdef9876543210 (Acme Retail Store)
    private static final String VALID_SDK_KEY = "os_pub_demo9876543210abcdef9876543210";

    @Test
    void testRegisterDeviceViaSdkKeySuccess() throws Exception {
        DeviceDto.RegisterDeviceRequest request = DeviceDto.RegisterDeviceRequest.builder()
                .platform(DevicePlatform.ANDROID)
                .pushToken("fcm_integration_test_token_abc_123")
                .externalUserId("user_shopper_99")
                .deviceModel("Samsung Galaxy S24")
                .osVersion("Android 14")
                .tags(Map.of("preference", "shoes", "vip", "true"))
                .build();

        mockMvc.perform(post("/sdk/v1/devices")
                        .header("X-Sdk-Key", VALID_SDK_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("ANDROID"))
                .andExpect(jsonPath("$.externalUserId").value("user_shopper_99"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void testRegisterDeviceWithoutSdkKeyForbidden() throws Exception {
        DeviceDto.RegisterDeviceRequest request = DeviceDto.RegisterDeviceRequest.builder()
                .platform(DevicePlatform.ANDROID)
                .pushToken("token_without_key")
                .build();

        mockMvc.perform(post("/sdk/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
