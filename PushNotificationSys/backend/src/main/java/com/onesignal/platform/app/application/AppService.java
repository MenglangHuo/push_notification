package com.onesignal.platform.app.application;

import com.onesignal.platform.app.api.dto.AppDto;
import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.domain.ProviderCredential;
import com.onesignal.platform.app.domain.ProviderType;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.app.infrastructure.ProviderCredentialRepository;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.ConflictException;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.iam.domain.Organization;
import com.onesignal.platform.iam.infrastructure.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppService {

    private final AppRepository appRepository;
    private final OrganizationRepository organizationRepository;
    private final ProviderCredentialRepository credentialRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<AppDto.AppResponse> listAppsForOrg(UUID orgId) {
        return appRepository.findByOrganizationId(orgId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppDto.AppResponse getApp(UUID appId) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("App not found"));
        return toResponse(app);
    }

    @Transactional
    public AppDto.AppResponse createApp(UUID orgId, AppDto.CreateAppRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        if (appRepository.existsByOrganizationIdAndName(orgId, request.getName().trim())) {
            throw new ConflictException("App with name '" + request.getName() + "' already exists in this organization");
        }

        App app = App.builder()
                .organization(org)
                .name(request.getName().trim())
                .description(request.getDescription())
                .build();

        App saved = appRepository.save(app);

        auditLogService.record(
                orgId, saved.getId(), TenantContextHolder.getUserId(), "USER",
                "APP_CREATED", "app", saved.getId().toString(),
                null, Map.of("name", saved.getName()), null, null
        );

        return toResponse(saved);
    }

    @Transactional
    public AppDto.AppResponse updateApp(UUID appId, AppDto.UpdateAppRequest request) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("App not found"));

        Map<String, Object> before = Map.of("name", app.getName());
        app.setName(request.getName().trim());
        app.setDescription(request.getDescription());

        App updated = appRepository.save(app);

        auditLogService.record(
                app.getOrganization().getId(), app.getId(), TenantContextHolder.getUserId(), "USER",
                "APP_UPDATED", "app", app.getId().toString(),
                before, Map.of("name", updated.getName()), null, null
        );

        return toResponse(updated);
    }

    @Transactional
    public void deleteApp(UUID appId) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("App not found"));

        UUID orgId = app.getOrganization().getId();
        appRepository.delete(app);

        auditLogService.record(
                orgId, appId, TenantContextHolder.getUserId(), "USER",
                "APP_DELETED", "app", appId.toString(),
                Map.of("name", app.getName()), null, null, null
        );
    }

    private AppDto.AppResponse toResponse(App app) {
        List<ProviderType> configuredProviders = credentialRepository.findByAppId(app.getId()).stream()
                .filter(ProviderCredential::isActive)
                .map(ProviderCredential::getProviderType)
                .distinct()
                .collect(Collectors.toList());

        return AppDto.AppResponse.builder()
                .id(app.getId())
                .organizationId(app.getOrganization().getId())
                .name(app.getName())
                .description(app.getDescription())
                .configuredProviders(configuredProviders)
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
