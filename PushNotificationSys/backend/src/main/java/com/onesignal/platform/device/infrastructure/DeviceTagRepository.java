package com.onesignal.platform.device.infrastructure;

import com.onesignal.platform.device.domain.DeviceTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceTagRepository extends JpaRepository<DeviceTag, UUID> {

    @Query("SELECT DISTINCT dt.tagKey FROM DeviceTag dt JOIN dt.device d WHERE d.app.id = :appId ORDER BY dt.tagKey ASC")
    List<String> findDistinctTagKeysByAppId(@Param("appId") UUID appId);

    List<DeviceTag> findByDeviceId(UUID deviceId);
}
