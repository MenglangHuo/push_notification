package com.onesignal.platform.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.apikey.api.dto.ApiKeyDto;
import com.onesignal.platform.apikey.domain.KeyType;
import com.onesignal.platform.app.api.dto.AppDto;
import com.onesignal.platform.common.security.Argon2PasswordService;
import com.onesignal.platform.iam.api.dto.AuthDto;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppAdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Argon2PasswordService passwordEncoder;

    private String accessToken;
    private final UUID acmeOrgId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() throws Exception {
        User admin = userRepository.findByEmail("admin@pushnotify.local").orElse(null);
        if (admin != null) {
            admin.setPasswordHash(passwordEncoder.encode("Password123!"));
            userRepository.save(admin);
        }

        AuthDto.LoginRequest loginRequest = new AuthDto.LoginRequest();
        loginRequest.setEmail("admin@pushnotify.local");
        loginRequest.setPassword("Password123!");

        MvcResult loginResult = mockMvc.perform(post("/admin/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        this.accessToken = json.get("accessToken").asText();
    }

    @Test
    @DisplayName("List apps for Acme Org, create new App, generate VAPID and API keys")
    void endToEnd_app_credentials_and_apiKeys_lifecycle() throws Exception {
        // 1. List seeded apps
        mockMvc.perform(get("/admin/api/v1/organizations/" + acmeOrgId + "/apps")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").isNotEmpty());

        // 2. Create a new App
        AppDto.CreateAppRequest createAppReq = new AppDto.CreateAppRequest();
        createAppReq.setName("Acme Loyalty Club " + System.currentTimeMillis());
        createAppReq.setDescription("Rewards and discounts app");

        MvcResult createAppResult = mockMvc.perform(post("/admin/api/v1/organizations/" + acmeOrgId + "/apps")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAppReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        String newAppId = objectMapper.readTree(createAppResult.getResponse().getContentAsString()).get("id").asText();

        // 3. Generate VAPID key pair for the new app
        MvcResult vapidResult = mockMvc.perform(post("/admin/api/v1/apps/" + newAppId + "/credentials/vapid/generate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isNotEmpty())
                .andReturn();

        String vapidPublicKey = objectMapper.readTree(vapidResult.getResponse().getContentAsString()).get("publicKey").asText();
        assertThat(vapidPublicKey).isNotBlank();

        // 4. Create a SERVER_REST API Key for the app
        ApiKeyDto.CreateApiKeyRequest createKeyReq = new ApiKeyDto.CreateApiKeyRequest();
        createKeyReq.setName("Backend Ingestion Key");
        createKeyReq.setKeyType(KeyType.SERVER_REST);
        createKeyReq.setScopes(List.of("notification:create", "device:read"));

        MvcResult keyResult = mockMvc.perform(post("/admin/api/v1/apps/" + newAppId + "/api-keys")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createKeyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawSecretKey").value(org.hamcrest.Matchers.startsWith("os_live_")))
                .andReturn();

        JsonNode keyJson = objectMapper.readTree(keyResult.getResponse().getContentAsString());
        String keyId = keyJson.get("id").asText();
        String rawSecret = keyJson.get("rawSecretKey").asText();
        assertThat(rawSecret).isNotBlank();

        // 5. Rotate the API Key
        mockMvc.perform(post("/admin/api/v1/apps/" + newAppId + "/api-keys/" + keyId + "/rotate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawSecretKey").value(org.hamcrest.Matchers.startsWith("os_live_")));

        // 6. Revoke the API Key
        mockMvc.perform(delete("/admin/api/v1/apps/" + newAppId + "/api-keys/" + keyId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }
}
