package com.gatekeeper.auditlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.auditlog.dto.AuditLogFilter;
import com.gatekeeper.auditlog.dto.AuditLogSummaryResponse;
import com.gatekeeper.config.CorrelationIdFilter;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationRepository;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestRepository;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single recording API for governance audit events (Milestone 7:
 * Enterprise Audit Logging). Every producer in the codebase calls
 * {@link #record} instead of writing an {@link AuditLog} row directly - see
 * docs/API-Design.md's Audit Log API section for the full list of wired
 * producers.
 * <p>
 * {@link #record} is deliberately synchronous, in the caller's own
 * transaction - the opposite of this codebase's async/AFTER_COMMIT
 * side-effect pattern (GitHub check-run publishing). An audit record must be
 * authoritative: if the write fails, the action it records must roll back
 * too, not silently produce an ungoverned action with no trail. Callers
 * therefore invoke {@code record} as part of their own existing
 * {@code @Transactional} method, the same way {@code ReportPublicationService}
 * already saved its {@code AuditLog} row inline before this milestone.
 * <p>
 * There is no update or delete method, and none will ever be added -
 * immutability is enforced by this class's API surface, not by database
 * constraints alone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final OrganizationRepository organizationRepository;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void record(AuditEvent event) {
        Organization organization = organizationRepository.findById(event.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization not found with id: " + event.getOrganizationId()));

        Repository repository = event.getRepositoryId() == null ? null
                : repositoryRepository.findById(event.getRepositoryId()).orElse(null);
        PullRequest pullRequest = event.getPullRequestId() == null ? null
                : pullRequestRepository.findById(event.getPullRequestId()).orElse(null);
        AnalysisRun analysisRun = event.getAnalysisRunId() == null ? null
                : analysisRunRepository.findById(event.getAnalysisRunId()).orElse(null);
        User actor = event.getActorId() == null ? null
                : userRepository.findById(event.getActorId()).orElse(null);

        String correlationId = event.getCorrelationId() != null
                ? event.getCorrelationId()
                : MDC.get(CorrelationIdFilter.MDC_KEY);

        AuditLog auditLog = AuditLog.builder()
                .organization(organization)
                .repository(repository)
                .pullRequest(pullRequest)
                .analysisRun(analysisRun)
                .actor(actor)
                .eventType(event.getEventType())
                .summary(event.getSummary())
                .targetType(event.getTargetType())
                .targetId(event.getTargetId())
                .oldValue(toJson(event.getOldValue()))
                .newValue(toJson(event.getNewValue()))
                .correlationId(correlationId)
                .build();

        auditLogRepository.save(auditLog);
        // Milestone 9: Observability. event_type is AuditEventType's fixed enum -
        // bounded cardinality, never the repository/PR/user id also on this record.
        meterRegistry.counter("gatekeeper.audit.events", "event_type", event.getEventType().name()).increment();
    }

    public Page<AuditLogSummaryResponse> search(Long organizationId, AuditLogFilter filter, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findAll(
                AuditLogSpecifications.matching(organizationId, filter), pageable);
        return page.map(auditLog -> AuditLogSummaryResponse.from(auditLog, objectMapper));
    }

    public AuditLogSummaryResponse findByIdOrThrow(Long organizationId, Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .filter(entry -> entry.getOrganization().getId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Audit log entry not found with id: " + id));
        return AuditLogSummaryResponse.from(auditLog, objectMapper);
    }

    /** Streams every matching row (ignoring pageable's page/size) as CSV, newest first - the same filter the search endpoint uses. */
    public byte[] exportCsv(Long organizationId, AuditLogFilter filter) {
        List<AuditLog> rows = auditLogRepository.findAll(
                AuditLogSpecifications.matching(organizationId, filter),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "occurredAt"));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8)) {
            writer.write("id,eventType,occurredAt,actor,repository,pullRequestId,analysisRunId,targetType,targetId,summary,correlationId\n");
            for (AuditLog row : rows) {
                writer.write(csvRow(row));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate audit log CSV export.", ex);
        }
        return buffer.toByteArray();
    }

    private String csvRow(AuditLog row) {
        return String.join(",",
                csvField(row.getId()),
                csvField(row.getEventType()),
                csvField(row.getOccurredAt()),
                csvField(row.getActor() == null ? null : row.getActor().getFullName()),
                csvField(row.getRepository() == null ? null : row.getRepository().getFullName()),
                csvField(row.getPullRequest() == null ? null : row.getPullRequest().getId()),
                csvField(row.getAnalysisRun() == null ? null : row.getAnalysisRun().getId()),
                csvField(row.getTargetType()),
                csvField(row.getTargetId()),
                csvField(row.getSummary()),
                csvField(row.getCorrelationId())) + "\n";
    }

    private String csvField(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize audit event value; storing null instead.", ex);
            return null;
        }
    }
}
