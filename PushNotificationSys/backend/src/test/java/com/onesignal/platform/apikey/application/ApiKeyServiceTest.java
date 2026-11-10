package com.onesignal.platform.apikey.application;

import com.onesignal.platform.apikey.api.dto.ApiKeyDto;
import com.onesignal.platform.apikey.domain.ApiKey;
import com.onesignal.platform.apikey.domain.KeyType;
import com.onesignal.platform.apikey.infrastructure.ApiKeyRepository;
import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.iam.domain.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private AppRepository appRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private App app;
    private UUID appId;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        Organization org = Organization.builder().name("Acme").slug("acme").build();
        org.setId(UUID.randomUUID());

        app = App.builder().name("Retail Store").organization(org).build();
        app.setId(appId);
    }

    @Test
    @DisplayName("Create SERVER_REST API key starts with os_live_ and hashes value")
    void createKey_serverRest_success() {
        ApiKeyDto.CreateApiKeyRequest request = new ApiKeyDto.CreateApiKeyRequest();
        request.setName("Prod Backend");
        request.setKeyType(KeyType.SERVER_REST);
        request.setScopes(List.of("notification:create"));

        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> {
            ApiKey k = i.getArgument(0);
            k.setId(UUID.randomUUID());
            return k;
        });

        ApiKeyDto.ApiKeyCreatedResponse response = apiKeyService.createKey(appId, request);

        assertThat(response).isNotNull();
        assertThat(response.getRawSecretKey()).startsWith("os_live_");
        assertThat(response.getKeyPrefix()).isEqualTo(response.getRawSecretKey().substring(0, 14));
        assertThat(response.getScopes()).contains("notification:create");

        verify(apiKeyRepository).save(argThat(k ->
                !k.getKeyHash().equals(response.getRawSecretKey()) && k.getKeyHash().length() == 64
        ));
    }

    @Test
    @DisplayName("Create SDK_PUBLIC API key starts with os_pub_")
    void createKey_sdkPublic_success() {
        ApiKeyDto.CreateApiKeyRequest request = new ApiKeyDto.CreateApiKeyRequest();
        request.setName("Mobile App SDK");
        request.setKeyType(KeyType.SDK_PUBLIC);
        request.setAllowedOrigins(List.of("https://app.acme.com"));

        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> {
            ApiKey k = i.getArgument(0);
            k.setId(UUID.randomUUID());
            return k;
        });

        ApiKeyDto.ApiKeyCreatedResponse response = apiKeyService.createKey(appId, request);

        assertThat(response.getRawSecretKey()).startsWith("os_pub_");
        assertThat(response.getAllowedOrigins()).contains("https://app.acme.com");
    }

    @Test
    @DisplayName("Verify and touch key updates last_used_at timestamp")
    void verifyAndTouchKey_validKey_touchesTimestamp() {
        String rawKey = "os_live_mocksecret1234567890abcdef";
        String hash = apiKeyService.hashKey(rawKey);

        ApiKey key = ApiKey.builder()
                .app(app)
                .keyType(KeyType.SERVER_REST)
                .name("Backend")
                .keyPrefix("os_live_mockse")
                .keyHash(hash)
                .revoked(false)
                .build();

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any())).thenReturn(key);

        Optional<ApiKey> result = apiKeyService.verifyAndTouchKey(rawKey, KeyType.SERVER_REST);

        assertThat(result).isPresent();
        assertThat(key.getLastUsedAt()).isNotNull();
        verify(apiKeyRepository).save(key);
    }
}
