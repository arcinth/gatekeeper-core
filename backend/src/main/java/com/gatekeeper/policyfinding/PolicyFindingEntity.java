package com.gatekeeper.policyfinding;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
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
 * Persisted form of the frozen PolicyFinding record (Milestone 3). Deliberately
 * not the record itself, and deliberately not extending BaseEntity: PolicyFinding
 * is Policy Engine's in-memory value type and must never gain JPA annotations
 * (that would be a redesign of frozen code), and findings - like AnalysisRuns -
 * are write-once with no update path, so there's no updated_at to justify
 * BaseEntity's auditing machinery (same reasoning as RefreshToken in Sprint 1).
 * Scoped to Policy Engine specifically rather than a generic polymorphic
 * findings table (Milestone 4 Architecture, ADR-015).
 */
@Getter
@Setter
@Entity
@Table(name = "policy_findings")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyFindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PolicyCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicySeverity severity;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false, length = 2000)
    private String recommendation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
