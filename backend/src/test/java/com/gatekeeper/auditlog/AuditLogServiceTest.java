package com.gatekeeper.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.auditlog.dto.AuditLogFilter;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationRepository;
import com.gatekeeper.pullrequest.PullRequestRepository;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class AuditLogServiceTest {

    private static final Long ORG_ID = 4L;

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final RepositoryRepository repositoryRepository = mock(RepositoryRepository.class);
    private final PullRequestRepository pullRequestRepository = mock(PullRequestRepository.class);
    private final AnalysisRunRepository analysisRunRepository = mock(AnalysisRunRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AuditLogService service = new AuditLogService(
            auditLogRepository, organizationRepository, repositoryRepository,
            pullRequestRepository, analysisRunRepository, userRepository, objectMapper, meterRegistry);

    private final Organization organization = Organization.builder().name("Acme").build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(auditLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void record_throwsResourceNotFoundWhenTheOrganizationDoesNotExist() {
        when(organizationRepository.findById(404L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class, () ->
                service.record(AuditEvent.builder()
                        .eventType(AuditEventType.USER_CREATED)
                        .organizationId(404L)
                        .summary("x")
                        .build()));
    }

    @Test
    void record_savesAnAuditLogWithTheResolvedOrganizationAndSerializedOldNewValues() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        service.record(AuditEvent.builder()
                .eventType(AuditEventType.POLICY_CONFIGURATION_CHANGED)
                .organizationId(ORG_ID)
                .targetType(AuditTargetType.POLICY_RULE)
                .targetId("TODO_COMMENT")
                .oldValue(Map.of("enabled", true))
                .newValue(Map.of("enabled", false))
                .summary("Policy rule 'TODO_COMMENT' configuration updated.")
                .build());

        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getOrganization()).isSameAs(organization);
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.POLICY_CONFIGURATION_CHANGED);
        assertThat(saved.getTargetType()).isEqualTo(AuditTargetType.POLICY_RULE);
        assertThat(saved.getTargetId()).isEqualTo("TODO_COMMENT");
        assertThat(saved.getOldValue()).isEqualTo("{\"enabled\":true}");
        assertThat(saved.getNewValue()).isEqualTo("{\"enabled\":false}");
    }

    @Test
    void record_toleratesUnresolvableAssociationsByLeavingThemNull() {
        when(repositoryRepository.findById(999L)).thenReturn(Optional.empty());
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        service.record(AuditEvent.builder()
                .eventType(AuditEventType.REPOSITORY_REMOVED)
                .organizationId(ORG_ID)
                .repositoryId(999L)
                .summary("Repository removed.")
                .build());

        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getRepository()).isNull();
    }

    @Test
    void record_leavesOldNewValueNullWhenNotProvided() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        service.record(AuditEvent.builder()
                .eventType(AuditEventType.VERDICT_PRODUCED)
                .organizationId(ORG_ID)
                .summary("Verdict produced.")
                .build());

        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOldValue()).isNull();
        assertThat(captor.getValue().getNewValue()).isNull();
    }

    @Test
    void record_populatesCorrelationIdFromMdcWhenNotExplicitlyProvided() {
        org.slf4j.MDC.put(com.gatekeeper.config.CorrelationIdFilter.MDC_KEY, "req-123");
        try {
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

            service.record(AuditEvent.builder()
                    .eventType(AuditEventType.VERDICT_PRODUCED)
                    .organizationId(ORG_ID)
                    .summary("Verdict produced.")
                    .build());

            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getCorrelationId()).isEqualTo("req-123");
        } finally {
            org.slf4j.MDC.remove(com.gatekeeper.config.CorrelationIdFilter.MDC_KEY);
        }
    }

    @Test
    void search_delegatesToTheRepositoryWithOrganizationScopedSpecificationAndMapsResults() {
        AuditLog entry = AuditLog.builder()
                .organization(organization)
                .eventType(AuditEventType.USER_CREATED)
                .summary("User created.")
                .build();
        Pageable pageable = PageRequest.of(0, 25);
        Page<AuditLog> page = new PageImpl<>(java.util.List.of(entry), pageable, 1);
        when(auditLogRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AuditLog>>any(), org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(page);

        var result = service.search(ORG_ID, new AuditLogFilter(null, null, null, null, null, null, null), pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).summary()).isEqualTo("User created.");
    }

    @Test
    void findByIdOrThrow_throwsWhenTheEntryBelongsToADifferentOrganization() {
        Organization otherOrganization = Organization.builder().name("Other").build();
        ReflectionTestUtils.setField(otherOrganization, "id", 999L);
        AuditLog entry = AuditLog.builder()
                .organization(otherOrganization)
                .eventType(AuditEventType.USER_CREATED)
                .summary("User created.")
                .build();
        when(auditLogRepository.findById(1L)).thenReturn(Optional.of(entry));

        org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class,
                () -> service.findByIdOrThrow(ORG_ID, 1L));
    }

    @Test
    void findByIdOrThrow_returnsTheEntryWhenItBelongsToTheCallersOrganization() {
        AuditLog entry = AuditLog.builder()
                .organization(organization)
                .eventType(AuditEventType.USER_CREATED)
                .summary("User created.")
                .build();
        when(auditLogRepository.findById(1L)).thenReturn(Optional.of(entry));

        var result = service.findByIdOrThrow(ORG_ID, 1L);

        assertThat(result.summary()).isEqualTo("User created.");
        assertThat(result.organizationId()).isEqualTo(ORG_ID);
    }

    @Test
    void exportCsv_writesAHeaderRowAndOneRowPerMatchingEntry() {
        AuditLog entry = AuditLog.builder()
                .organization(organization)
                .eventType(AuditEventType.USER_CREATED)
                .summary("User \"Ada\" created.")
                .occurredAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(auditLogRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AuditLog>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.List.of(entry));

        byte[] csv = service.exportCsv(ORG_ID, new AuditLogFilter(null, null, null, null, null, null, null));
        String content = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(content).startsWith("id,eventType,occurredAt,actor,repository,pullRequestId,analysisRunId,targetType,targetId,summary,correlationId\n");
        assertThat(content).contains("\"User \"\"Ada\"\" created.\"");
    }

    @Test
    void record_resolvesTheActorWhenAnActorIdIsProvided() {
        User actor = User.builder().email("ada@example.com").fullName("Ada").build();
        ReflectionTestUtils.setField(actor, "id", 55L);
        when(userRepository.findById(55L)).thenReturn(Optional.of(actor));
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        service.record(AuditEvent.builder()
                .eventType(AuditEventType.USER_CREATED)
                .organizationId(ORG_ID)
                .actorId(55L)
                .summary("User created.")
                .build());

        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isSameAs(actor);
    }
}
