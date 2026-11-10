package com.onesignal.platform.device.application;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.device.api.dto.DeviceDto;
import com.onesignal.platform.device.domain.Device;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.device.domain.DeviceTag;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.device.infrastructure.DeviceTagRepository;
import com.onesignal.platform.app.domain.ProviderType;
import com.onesignal.platform.app.infrastructure.ProviderCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRegistrationService {

    private final DeviceRepository deviceRepository;
    private final DeviceTagRepository deviceTagRepository;
    private final AppRepository appRepository;
    private final ProviderCredentialRepository credentialRepository;

    @Transactional
    public DeviceDto.DeviceResponse registerDevice(UUID appId, DeviceDto.RegisterDeviceRequest request) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("App not found: " + appId));

        String token = request.getPushToken().trim();
        Optional<Device> existingOpt = deviceRepository.findByAppIdAndPushToken(appId, token);

        Device device;
        if (existingOpt.isPresent()) {
            device = existingOpt.get();
            device.setPlatform(request.getPlatform());
            device.setActive(true);
            device.setLastActiveAt(Instant.now());
            if (request.getExternalUserId() != null) {
                device.setExternalUserId(request.getExternalUserId().trim());
            }
            if (request.getWebAuthKey() != null) {
                device.setWebAuthKey(request.getWebAuthKey());
            }
            if (request.getWebP256dhKey() != null) {
                device.setWebP256dhKey(request.getWebP256dhKey());
            }
            if (request.getDeviceModel() != null) {
                device.setDeviceModel(request.getDeviceModel());
            }
            if (request.getOsVersion() != null) {
                device.setOsVersion(request.getOsVersion());
            }
            if (request.getAppVersion() != null) {
                device.setAppVersion(request.getAppVersion());
            }
            if (request.getLanguage() != null) {
                device.setLanguage(request.getLanguage());
            }
            if (request.getTimezone() != null) {
                device.setTimezone(request.getTimezone());
            }
        } else {
            device = Device.builder()
                    .app(app)
                    .platform(request.getPlatform())
                    .pushToken(token)
                    .externalUserId(request.getExternalUserId() != null ? request.getExternalUserId().trim() : null)
                    .webAuthKey(request.getWebAuthKey())
                    .webP256dhKey(request.getWebP256dhKey())
                    .deviceModel(request.getDeviceModel() != null ? request.getDeviceModel() : "Unknown")
                    .osVersion(request.getOsVersion())
                    .appVersion(request.getAppVersion())
                    .language(request.getLanguage() != null ? request.getLanguage() : "en")
                    .timezone(request.getTimezone() != null ? request.getTimezone() : "UTC")
                    .active(true)
                    .lastActiveAt(Instant.now())
                    .build();
        }

        Device savedDevice = deviceRepository.save(device);

        // Update tags if supplied
        if (request.getTags() != null) {
            if (savedDevice.getTags() == null) {
                savedDevice.setTags(new ArrayList<>());
            }

            Map<String, DeviceTag> existingTagMap = savedDevice.getTags().stream()
                    .collect(Collectors.toMap(DeviceTag::getTagKey, t -> t, (t1, t2) -> t1));

            // Remove tags no longer in request
            savedDevice.getTags().removeIf(t -> !request.getTags().containsKey(t.getTagKey()));

            // Update existing or add new
            for (Map.Entry<String, String> entry : request.getTags().entrySet()) {
                DeviceTag existingTag = existingTagMap.get(entry.getKey());
                if (existingTag != null) {
                    existingTag.setTagValue(entry.getValue());
                } else {
                    savedDevice.getTags().add(DeviceTag.builder()
                            .device(savedDevice)
                            .tagKey(entry.getKey())
                            .tagValue(entry.getValue())
                            .build());
                }
            }
        }

        log.info("Registered device {} for app {} on platform {}", savedDevice.getId(), appId, savedDevice.getPlatform());
        return toDeviceResponse(savedDevice);
    }

    @Transactional
    public void deactivateDevice(UUID appId, String pushToken, String reason) {
        log.warn("Deactivating device with token {} for app {} due to: {}", maskToken(pushToken), appId, reason);
        deviceRepository.deactivateToken(appId, pushToken);
    }

    @Transactional(readOnly = true)
    public Page<DeviceDto.DeviceResponse> listDevices(UUID appId, Boolean active, Pageable pageable) {
        Page<Device> page = (active != null)
                ? deviceRepository.findByAppIdAndActive(appId, active, pageable)
                : deviceRepository.findByAppId(appId, pageable);

        return page.map(this::toDeviceResponse);
    }

    @Transactional(readOnly = true)
    public DeviceDto.DeviceStatsResponse getStats(UUID appId) {
        long total = deviceRepository.countByAppId(appId);
        long active = deviceRepository.countByAppIdAndActiveTrue(appId);
        long android = deviceRepository.countByAppIdAndPlatformAndActiveTrue(appId, DevicePlatform.ANDROID);
        long ios = deviceRepository.countByAppIdAndPlatformAndActiveTrue(appId, DevicePlatform.IOS);
        long web = deviceRepository.countByAppIdAndPlatformAndActiveTrue(appId, DevicePlatform.WEB);

        return DeviceDto.DeviceStatsResponse.builder()
                .totalDevices(total)
                .activeDevices(active)
                .androidActive(android)
                .iosActive(ios)
                .webActive(web)
                .build();
    }

    @Transactional
    public void deleteDevice(UUID appId, UUID deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .filter(d -> d.getApp().getId().equals(appId))
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
        deviceRepository.delete(device);
        log.info("Deleted device {} from app {}", deviceId, appId);
    }

    public DeviceDto.DeviceResponse toDeviceResponse(Device device) {
        Map<String, String> tagsMap = new HashMap<>();
        if (device.getTags() != null) {
            for (DeviceTag tag : device.getTags()) {
                tagsMap.put(tag.getTagKey(), tag.getTagValue());
            }
        }

        return DeviceDto.DeviceResponse.builder()
                .id(device.getId())
                .appId(device.getApp().getId())
                .externalUserId(device.getExternalUserId())
                .platform(device.getPlatform())
                .pushTokenMasked(maskToken(device.getPushToken()))
                .deviceModel(device.getDeviceModel())
                .osVersion(device.getOsVersion())
                .appVersion(device.getAppVersion())
                .language(device.getLanguage())
                .timezone(device.getTimezone())
                .active(device.isActive())
                .lastActiveAt(device.getLastActiveAt())
                .createdAt(device.getCreatedAt())
                .tags(tagsMap)
                .build();
    }

    @Transactional(readOnly = true)
    public String getVapidPublicKey(UUID appId) {
        return credentialRepository.findByAppIdAndProviderType(appId, ProviderType.WEBPUSH)
                .map(cred -> {
                    if (cred.getMetadataMasked() != null && cred.getMetadataMasked().get("public_key") != null) {
                        return cred.getMetadataMasked().get("public_key").toString();
                    }
                    return null;
                })
                .orElse(null);
    }

    public static String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "******";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
