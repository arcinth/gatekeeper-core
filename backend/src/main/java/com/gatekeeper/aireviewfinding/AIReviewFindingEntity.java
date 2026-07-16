package com.gatekeeper.aireviewfinding;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewrun.AIReviewRun;
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
 * Persisted form of the frozen AIReviewFinding record (Sprint 4 Milestone 1).
 * Structurally mirrors SecurityFindingEntity/PolicyFindingEntity - write-once,
 * not extending BaseEntity, scoped to its own engine (ADR-015's precedent
 * applied a third time) - with two deliberate differences reflecting
 * AIReviewFinding's own shape, not an inconsistency:
 * <ul>
 *   <li>{@code lineNumber} is nullable ({@code Integer}, not primitive
 *       {@code int}) - an AI observation may be file-level, unlike a
 *       deterministic rule's finding, which always points at a specific line.</li>
 *   <li>{@code recommendation} is nullable - a plain observation with no
 *       actionable advice is still a valid AI finding, unlike a deterministic
 *       rule, which always has a fixed recommendation baked into it.</li>
 * </ul>
 * Belongs to an AIReviewRun, not directly to an AnalysisRun - see
 * AIReviewRun's own Javadoc for why that indirection exists.
 */
@Getter
@Setter
@Entity
@Table(name = "ai_review_findings")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIReviewFindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_review_run_id", nullable = false)
    private AIReviewRun aiReviewRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AIReviewFindingType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AIReviewConfidence confidence;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(length = 2000)
    private String recommendation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
