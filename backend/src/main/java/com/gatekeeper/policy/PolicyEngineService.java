package com.gatekeeper.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The application-facing boundary around PolicyEngine. This split exists so
 * PolicyEngine can stay a pure, trivially-unit-testable computation (rules in,
 * findings out, no logging noise to assert around) while this class carries
 * the concerns a caller in another module actually needs: structured
 * before/after logging, and - once AnalysisOrchestrator is extended to invoke
 * engines and Database.md's Finding table exists - the eventual place
 * PolicyFinding persistence would be added, without PolicyEngine itself ever
 * needing to know persistence exists. Neither of those integrations is built
 * yet; this milestone stops at a fully self-contained, independently testable
 * Policy Engine (see the Milestone 3 writeup for why orchestration wiring is
 * deferred).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEngineService {

    private final PolicyEngine policyEngine;

    public PolicyResult evaluate(PolicyContext context) {
        log.info("Starting policy evaluation for analysis run {} (repository '{}', {} file(s)).",
                context.analysisRunId(), context.repositoryFullName(), context.changedFiles().size());

        PolicyResult result = policyEngine.evaluate(context);

        log.info("Completed policy evaluation for analysis run {}: {} finding(s) from {} rule(s).",
                context.analysisRunId(), result.findings().size(), result.rulesEvaluated());

        return result;
    }
}
