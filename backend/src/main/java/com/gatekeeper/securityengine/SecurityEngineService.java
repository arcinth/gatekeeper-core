package com.gatekeeper.securityengine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The application-facing boundary around SecurityEngine. This split exists so
 * SecurityEngine can stay a pure, trivially-unit-testable computation (rules
 * in, findings out, no logging noise to assert around) while this class
 * carries the concerns a caller in another module actually needs: structured
 * before/after logging - mirrors com.gatekeeper.policy.PolicyEngineService
 * exactly. Orchestration wiring (AnalysisExecutionService invoking this,
 * persistence of SecurityFindings) is out of scope for this milestone - see
 * the Sprint 3 Security Engine Architecture for the integration design,
 * which a later milestone of this sprint implements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEngineService {

    private final SecurityEngine securityEngine;

    public SecurityResult evaluate(SecurityContext context) {
        log.info("Starting security evaluation for analysis run {} (repository '{}', {} file(s)).",
                context.analysisRunId(), context.repositoryFullName(), context.changedFiles().size());

        SecurityResult result = securityEngine.evaluate(context);

        log.info("Completed security evaluation for analysis run {}: {} finding(s) from {} rule(s).",
                context.analysisRunId(), result.findings().size(), result.rulesEvaluated());

        return result;
    }
}
