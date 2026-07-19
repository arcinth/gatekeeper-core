package com.gatekeeper.auditlog;

import com.gatekeeper.common.ApiResponse;
import com.gatekeeper.common.dto.PageResponse;
import com.gatekeeper.auditlog.dto.AuditLogFilter;
import com.gatekeeper.auditlog.dto.AuditLogSummaryResponse;
import com.gatekeeper.security.SecurityUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only, organization-scoped Audit Log search API (Milestone 7:
 * Enterprise Audit Logging). No write endpoints: entries are created only by
 * {@link AuditLogService#record}, called from the modules that produce
 * governance events - never directly by a client of this API.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ApiResponse<PageResponse<AuditLogSummaryResponse>> search(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) Long pullRequestId,
            @RequestParam(required = false) Long analysisRunId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) Instant occurredAfter,
            @RequestParam(required = false) Instant occurredBefore,
            @AuthenticationPrincipal SecurityUser principal,
            @PageableDefault(size = 25, sort = "occurredAt", direction = Direction.DESC) Pageable pageable) {
        AuditLogFilter filter = new AuditLogFilter(
                eventType, repositoryId, pullRequestId, analysisRunId, actorId, occurredAfter, occurredBefore);
        return ApiResponse.ok(PageResponse.from(
                auditLogService.search(principal.getOrganizationId(), filter, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AuditLogSummaryResponse> findById(
            @PathVariable Long id, @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(auditLogService.findByIdOrThrow(principal.getOrganizationId(), id));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) Long pullRequestId,
            @RequestParam(required = false) Long analysisRunId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) Instant occurredAfter,
            @RequestParam(required = false) Instant occurredBefore,
            @AuthenticationPrincipal SecurityUser principal) {
        AuditLogFilter filter = new AuditLogFilter(
                eventType, repositoryId, pullRequestId, analysisRunId, actorId, occurredAfter, occurredBefore);
        byte[] csv = auditLogService.exportCsv(principal.getOrganizationId(), filter);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .body(csv);
    }
}
