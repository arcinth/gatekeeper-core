package com.gatekeeper.verdictengine;

import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.securityengine.SecurityFinding;
import java.util.List;

/**
 * Everything a VerdictRule needs to evaluate one AnalysisRun - and,
 * structurally, nothing else (Sprint 5 Architecture, Section 7). There is no
 * field for AI Review data of any kind: this is the concrete enforcement of
 * "ignore AI Review findings completely" (ADR-037) - not a runtime check a
 * future maintainer could bypass, but a contract a VerdictRule implementation
 * is structurally incapable of violating, since the type has nowhere to put
 * AI data even if a caller wanted to.
 * <p>
 * policyFindings/securityFindings are the same frozen domain records
 * PolicyEngine/SecurityEngine already produce (PolicyResult#findings(),
 * SecurityResult#findings()) - no new mapping, no re-querying required to
 * build one.
 * <p>
 * repositoryFullName is included for message-building (a VerdictReason reads
 * better naming the repository) and as a future organization-scoped
 * threshold lookup anchor (Sprint 5 Architecture, Section 18) - not consumed
 * by either of Milestone 1's concrete rules.
 *
 * @param analysisRunId      identifies which AnalysisRun this evaluation belongs to
 * @param repositoryFullName the "org/repo" the AnalysisRun's PullRequest belongs to
 * @param policyFindings     every PolicyFinding produced for this AnalysisRun
 * @param securityFindings   every SecurityFinding produced for this AnalysisRun
 */
public record VerdictContext(
        Long analysisRunId,
        String repositoryFullName,
        List<PolicyFinding> policyFindings,
        List<SecurityFinding> securityFindings) {
}
