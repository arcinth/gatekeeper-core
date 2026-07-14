package com.gatekeeper.analysisrun;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {

    Optional<AnalysisRun> findByPullRequestIdAndCommitSha(Long pullRequestId, String commitSha);
}
