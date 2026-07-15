package com.gatekeeper.securityfinding;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
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
 * Persisted form of the frozen SecurityFinding record (Sprint 3 Milestone 1).
 * Mirrors com.gatekeeper.policyfinding.PolicyFindingEntity exactly - not
 * extending BaseEntity for the same reason (write-once, no updated_at to
 * justify the auditing machinery), scoped to the Security Engine specifically
 * rather than a shared polymorphic findings table (ADR-015's precedent,
 * applied consistently - see also ADR-022 on why SecurityFinding itself isn't
 * a shared type with PolicyFinding).
 */
@Getter
@Setter
@Entity
@Table(name = "security_findings")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityFindingEntity {

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
    private SecurityCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecuritySeverity severity;

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
