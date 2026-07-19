package com.gatekeeper.reviewdecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.auditlog.AuditEvent;
import com.gatekeeper.auditlog.AuditLogService;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.reviewdecision.dto.CreateReviewDecisionRequest;
import com.gatekeeper.reviewdecision.dto.ReviewDecisionResponse;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewDecisionServiceTest {

    private final ReviewDecisionRepository reviewDecisionRepository = mock(ReviewDecisionRepository.class);
    private final AnalysisRunRepository analysisRunRepository = mock(AnalysisRunRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final ReviewDecisionService service = new ReviewDecisionService(
            reviewDecisionRepository, analysisRunRepository, userRepository, eventPublisher, auditLogService);

    private final Organization organization = Organization.builder().name("Acme").build();
    private final Repository repository = Repository.builder().organization(organization).fullName("acme/core").build();
    private final PullRequest pullRequest = PullRequest.builder().repository(repository).number(7).build();
    private final AnalysisRun analysisRun = AnalysisRun.builder().pullRequest(pullRequest).build();
    private final User reviewer = User.builder().fullName("Ada Reviewer").email("ada@example.com").build();

    @BeforeEach
    void assignReviewerId() {
        ReflectionTestUtils.setField(organization, "id", 4L);
        ReflectionTestUtils.setField(repository, "id", 5L);
        ReflectionTestUtils.setField(pullRequest, "id", 6L);
        ReflectionTestUtils.setField(reviewer, "id", 9L);
    }

    @Test
    void create_persistsAndReturnsTheDecisionWhenTheAnalysisRunAndReviewerExist() {
        when(analysisRunRepository.findById(1L)).thenReturn(Optional.of(analysisRun));
        when(userRepository.findById(9L)).thenReturn(Optional.of(reviewer));
        when(reviewDecisionRepository.save(any(ReviewDecision.class))).thenAnswer(invocation -> {
            ReviewDecision saved = invocation.getArgument(0);
            saved.setId(42L);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        ReviewDecisionResponse result = service.create(
                1L, 9L, new CreateReviewDecisionRequest(ReviewDecisionType.APPROVED, "Looks good"));

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.decision()).isEqualTo(ReviewDecisionType.APPROVED);
        assertThat(result.comment()).isEqualTo("Looks good");
        assertThat(result.reviewerId()).isEqualTo(9L);
        assertThat(result.reviewerName()).isEqualTo("Ada Reviewer");
        verify(eventPublisher).publishEvent(new ReviewDecisionRecordedEvent(1L));

        org.mockito.ArgumentCaptor<AuditEvent> auditCaptor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActorId()).isEqualTo(9L);
        assertThat(auditCaptor.getValue().getOrganizationId()).isEqualTo(4L);
        assertThat(auditCaptor.getValue().getAnalysisRunId()).isEqualTo(1L);
    }

    @Test
    void create_throwsResourceNotFoundWhenTheAnalysisRunDoesNotExist() {
        when(analysisRunRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                404L, 9L, new CreateReviewDecisionRequest(ReviewDecisionType.APPROVED, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reviewDecisionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void create_throwsResourceNotFoundWhenTheReviewerDoesNotExist() {
        when(analysisRunRepository.findById(1L)).thenReturn(Optional.of(analysisRun));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                1L, 404L, new CreateReviewDecisionRequest(ReviewDecisionType.REJECTED, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reviewDecisionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void findHistory_returnsDecisionsNewestFirstWhenTheAnalysisRunExists() {
        when(analysisRunRepository.existsById(1L)).thenReturn(true);
        ReviewDecision older = ReviewDecision.builder()
                .id(1L).analysisRun(analysisRun).reviewer(reviewer)
                .decision(ReviewDecisionType.REJECTED).createdAt(Instant.now().minusSeconds(60)).build();
        ReviewDecision newer = ReviewDecision.builder()
                .id(2L).analysisRun(analysisRun).reviewer(reviewer)
                .decision(ReviewDecisionType.APPROVED).createdAt(Instant.now()).build();
        when(reviewDecisionRepository.findByAnalysisRunIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(newer, older));

        List<ReviewDecisionResponse> result = service.findHistory(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2L);
        assertThat(result.get(1).id()).isEqualTo(1L);
    }

    @Test
    void findHistory_throwsResourceNotFoundWhenTheAnalysisRunDoesNotExist() {
        when(analysisRunRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.findHistory(404L)).isInstanceOf(ResourceNotFoundException.class);

        verify(reviewDecisionRepository, never()).findByAnalysisRunIdOrderByCreatedAtDesc(any());
    }
}
