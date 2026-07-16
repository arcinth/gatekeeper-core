package com.gatekeeper.verdict;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Extended for the read/query API (Sprint 5 Milestone 3). */
public interface VerdictReasonRepository extends JpaRepository<VerdictReasonEntity, Long> {

    /** Every reason for one Verdict, in a stable order - backs both the verdict detail endpoint and AnalysisRunDetailResponse's embedded reasons. */
    List<VerdictReasonEntity> findByVerdictIdOrderById(Long verdictId);

    /** Batched per-verdict reasons count for the verdicts list view. */
    @Query("SELECT r.verdict.id, COUNT(r) FROM VerdictReasonEntity r "
            + "WHERE r.verdict.id IN :verdictIds GROUP BY r.verdict.id")
    List<Object[]> countByVerdictIdIn(@Param("verdictIds") Collection<Long> verdictIds);
}
