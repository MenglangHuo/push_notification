package com.onesignal.platform.device.infrastructure;

import com.onesignal.platform.device.domain.Device;
import com.onesignal.platform.device.domain.DevicePlatform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID>, JpaSpecificationExecutor<Device> {

    Optional<Device> findByAppIdAndPushToken(UUID appId, String pushToken);

    Page<Device> findByAppId(UUID appId, Pageable pageable);

    Page<Device> findByAppIdAndActive(UUID appId, boolean active, Pageable pageable);

    Page<Device> findByAppIdAndPlatform(UUID appId, DevicePlatform platform, Pageable pageable);

    Page<Device> findByAppIdAndPlatformAndActive(UUID appId, DevicePlatform platform, boolean active, Pageable pageable);

    List<Device> findByAppIdAndActiveTrue(UUID appId);

    List<Device> findByAppIdAndPlatformAndActiveTrue(UUID appId, DevicePlatform platform);

    List<Device> findByAppIdAndIdInAndActiveTrue(UUID appId, Collection<UUID> ids);

    List<Device> findByAppIdAndPushTokenInAndActiveTrue(UUID appId, Collection<String> pushTokens);

    List<Device> findByAppIdAndExternalUserIdInAndActiveTrue(UUID appId, Collection<String> externalUserIds);

    long countByAppId(UUID appId);

    long countByAppIdAndActiveTrue(UUID appId);

    long countByAppIdAndPlatformAndActiveTrue(UUID appId, DevicePlatform platform);

    @Modifying
    @Query("UPDATE Device d SET d.active = false, d.updatedAt = CURRENT_TIMESTAMP WHERE d.app.id = :appId AND d.pushToken = :pushToken")
    void deactivateToken(@Param("appId") UUID appId, @Param("pushToken") String pushToken);

    @Query("SELECT CAST(d.createdAt AS LocalDate), COUNT(d) FROM Device d WHERE d.app.id = :appId AND d.createdAt >= :since GROUP BY CAST(d.createdAt AS LocalDate) ORDER BY CAST(d.createdAt AS LocalDate)")
    List<Object[]> countNewDevicesPerDay(@Param("appId") UUID appId, @Param("since") Instant since);

    @Query("SELECT CAST(d.updatedAt AS LocalDate), COUNT(d) FROM Device d WHERE d.app.id = :appId AND d.active = false AND d.updatedAt >= :since GROUP BY CAST(d.updatedAt AS LocalDate) ORDER BY CAST(d.updatedAt AS LocalDate)")
    List<Object[]> countChurnedDevicesPerDay(@Param("appId") UUID appId, @Param("since") Instant since);
}
