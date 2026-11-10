package com.onesignal.platform.audit.api;

import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.audit.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/admin/api/v1/organizations/{orgId}/audit-logs", "/admin/api/v1/orgs/{orgId}/audit-logs"})
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasPermission(#orgId, 'org', 'audit:read')")
    public ResponseEntity<Page<AuditLog>> listAuditLogs(
            @PathVariable UUID orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(auditLogService.getLogsForOrg(orgId, PageRequest.of(page, size)));
    }

    @GetMapping("/export")
    @PreAuthorize("hasPermission(#orgId, 'org', 'audit:export')")
    public ResponseEntity<String> exportAuditLogsCsv(@PathVariable UUID orgId) {
        String csv = auditLogService.exportToCsv(orgId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs-" + orgId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
