package com.onesignal.platform.segment.infrastructure;

import com.onesignal.platform.segment.domain.Segment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SegmentRepository extends JpaRepository<Segment, UUID> {

    List<Segment> findByAppIdAndActiveTrueOrderByNameAsc(UUID appId);

    List<Segment> findByAppIdOrderByNameAsc(UUID appId);

    Optional<Segment> findByIdAndAppId(UUID id, UUID appId);

    boolean existsByAppIdAndName(UUID appId, String name);
}
