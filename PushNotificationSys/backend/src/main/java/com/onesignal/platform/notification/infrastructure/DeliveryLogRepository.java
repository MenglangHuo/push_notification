package com.onesignal.platform.notification.infrastructure;

import com.onesignal.platform.notification.domain.DeliveryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, UUID> {

    Page<DeliveryLog> findByNotificationIdOrderByCreatedAtDesc(UUID notificationId, Pageable pageable);

    Page<DeliveryLog> findByAppIdOrderByCreatedAtDesc(UUID appId, Pageable pageable);

    long countByAppId(UUID appId);

    long countByAppIdAndStatus(UUID appId, String status);

    @org.springframework.data.jpa.repository.Query("SELECT dl FROM DeliveryLog dl WHERE dl.appId = :appId " +
           "AND (:provider IS NULL OR dl.provider = :provider) " +
           "AND (:status IS NULL OR dl.status = :status) " +
           "AND (:notificationId IS NULL OR dl.notificationId = :notificationId) " +
           "ORDER BY dl.createdAt DESC")
    Page<DeliveryLog> findWithFilters(
            @org.springframework.data.repository.query.Param("appId") UUID appId,
            @org.springframework.data.repository.query.Param("provider") String provider,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("notificationId") UUID notificationId,
            Pageable pageable
    );

    @org.springframework.data.jpa.repository.Query("SELECT dl FROM DeliveryLog dl WHERE dl.appId = :appId ORDER BY dl.createdAt DESC")
    List<DeliveryLog> findAllForExport(@org.springframework.data.repository.query.Param("appId") UUID appId);

    @org.springframework.data.jpa.repository.Query("SELECT dl.provider, dl.status, COUNT(dl), AVG(COALESCE(dl.latencyMs, 0)) " +
           "FROM DeliveryLog dl WHERE dl.appId = :appId GROUP BY dl.provider, dl.status")
    List<Object[]> findProviderAggregates(@org.springframework.data.repository.query.Param("appId") UUID appId);

    List<DeliveryLog> findByAppIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID appId, java.time.Instant since);

    @Query("SELECT COALESCE(dl.errorMessage, 'Unknown'), COUNT(dl) " +
           "FROM DeliveryLog dl WHERE dl.appId = :appId AND dl.status = 'FAILED' " +
           "AND dl.createdAt >= :since GROUP BY dl.errorMessage ORDER BY COUNT(dl) DESC")
    List<Object[]> findFailureReasonAggregates(@Param("appId") UUID appId, @Param("since") Instant since);

    @Query("SELECT FUNCTION('date_part', 'dow', dl.createdAt), FUNCTION('date_part', 'hour', dl.createdAt), COUNT(dl) " +
           "FROM DeliveryLog dl WHERE dl.appId = :appId AND dl.createdAt >= :since " +
           "GROUP BY FUNCTION('date_part', 'dow', dl.createdAt), FUNCTION('date_part', 'hour', dl.createdAt)")
    List<Object[]> findHourlyDayOfWeekAggregates(@Param("appId") UUID appId, @Param("since") Instant since);

    @Query("SELECT dl.provider, dl.status, COUNT(dl), AVG(COALESCE(dl.latencyMs, 0)) " +
           "FROM DeliveryLog dl WHERE dl.appId = :appId AND dl.createdAt >= :since GROUP BY dl.provider, dl.status")
    List<Object[]> findProviderAggregatesAfter(@Param("appId") UUID appId, @Param("since") Instant since);
}
