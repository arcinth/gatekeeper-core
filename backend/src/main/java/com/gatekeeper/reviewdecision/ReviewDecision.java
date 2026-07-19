package com.gatekeeper.reviewdecision;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A human reviewer's recorded decision against one AnalysisRun (Milestone 2:
 * Reviewer Decision Workflow). Write-once, like Verdict/PolicyFindingEntity/
 * SecurityFindingEntity - not extending BaseEntity (no updated_at to
 * justify): a reviewer changing their mind creates a new ReviewDecision row,
 * never edits an existing one, so the full history stays intact.
 * <p>
 * Unlike Verdict, analysisRun carries no UNIQUE constraint here - multiple
 * decisions per run are expected (re-review after discussion); the most
 * recently created one is simply the current decision. Recording a
 * ReviewDecision has no effect on AnalysisRun/Verdict/PullRequest - it is
 * purely additive, observed data. GitHub Check Run write-back is out of
 * scope for this milestone (see V12__review_decisions.sql).
 */
@Getter
@Setter
@Entity
@Table(name = "review_decisions")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewDecisionType decision;

    @Column(length = 2000)
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
