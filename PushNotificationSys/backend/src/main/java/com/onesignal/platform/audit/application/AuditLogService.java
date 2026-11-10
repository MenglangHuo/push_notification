package com.onesignal.platform.audit.application;

import com.onesignal.platform.audit.domain.AuditLog;
import com.onesignal.platform.audit.infrastructure.AuditLogRepository;
import com.onesignal.platform.common.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(
            UUID organizationId,
            UUID appId,
            UUID actorId,
            String actorType,
            String action,
            String targetResource,
            String targetId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            String ipAddress,
            String userAgent
    ) {
        AuditLog entry = AuditLog.builder()
                .organizationId(organizationId != null ? organizationId : TenantContextHolder.getOrganizationId())
                .appId(appId != null ? appId : TenantContextHolder.getAppId())
                .actorId(actorId != null ? actorId : TenantContextHolder.getUserId())
                .actorType(actorType != null ? actorType : "USER")
                .action(action)
                .targetResource(targetResource)
                .targetId(targetId)
                .beforeState(beforeState)
                .afterState(afterState)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(entry);
        log.debug("Recorded audit log for action: {}, resource: {}", action, targetResource);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsForOrg(UUID orgId, Pageable pageable) {
        return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public String exportToCsv(UUID orgId) {
        List<AuditLog> logs = auditLogRepository.findTop1000ByOrganizationIdOrderByCreatedAtDesc(orgId);
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            pw.println("id,created_at,actor_id,actor_type,action,target_resource,target_id,ip_address");
            for (AuditLog l : logs) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        l.getId(),
                        l.getCreatedAt(),
                        l.getActorId() != null ? l.getActorId() : "",
                        l.getActorType(),
                        l.getAction(),
                        l.getTargetResource(),
                        l.getTargetId() != null ? l.getTargetId() : "",
                        l.getIpAddress() != null ? l.getIpAddress() : "");
            }
        }
        return sw.toString();
    }
}
