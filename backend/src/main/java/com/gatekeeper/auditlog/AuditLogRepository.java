package com.gatekeeper.auditlog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Ready for Milestone 2's Report Detail response to embed a run's audit trail; unused by Milestone 1's writer. */
    List<AuditLog> findByAnalysisRunIdOrderByOccurredAt(Long analysisRunId);
}
