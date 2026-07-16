package com.gatekeeper.verdict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Persisted form of one frozen VerdictReason record (Sprint 5 Milestone 1).
 * Write-once, mirroring SecurityFindingEntity/AIReviewFindingEntity - scoped
 * to its own parent (Verdict), not directly to AnalysisRun, the same one-
 * indirection-level pattern AIReviewFindingEntity already established for
 * AIReviewRun.
 * <p>
 * Deliberately no reference to a specific PolicyFinding/SecurityFinding row
 * (Sprint 5 Architecture, ADR-040) - see VerdictReason's own Javadoc for why.
 */
@Getter
@Setter
@Entity
@Table(name = "verdict_reasons")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerdictReasonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verdict_id", nullable = false)
    private Verdict verdict;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Column(nullable = false)
    private boolean blocking;

    @Column(nullable = false, length = 2000)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
