package com.gatekeeper.policy;

import com.gatekeeper.policyconfiguration.PolicyConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The application-facing boundary around PolicyEngine. This split exists so
 * PolicyEngine can stay a pure, trivially-unit-testable computation (rules in,
 * findings out, no logging noise to assert around) while this class carries
 * the concerns a caller in another module actually needs: structured
 * before/after logging, and - since Milestone 6 (Policy Management) - loading
 * the calling organization's {@link PolicyConfigurationSet} before invoking
 * the engine. PolicyEngine itself still never touches a database; this is
 * the one place that turns a query into the immutable snapshot it receives
 * as a plain parameter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEngineService {

    private final PolicyEngine policyEngine;
    private final PolicyConfigurationService policyConfigurationService;

    public PolicyResult evaluate(PolicyContext context) {
        log.info("Starting policy evaluation for analysis run {} (repository '{}', {} file(s)).",
                context.analysisRunId(), context.repositoryFullName(), context.changedFiles().size());

        PolicyConfigurationSet configuration = policyConfigurationService.buildConfigurationSet(context.organizationId());
        PolicyResult result = policyEngine.evaluate(context, configuration);

        log.info("Completed policy evaluation for analysis run {}: {} finding(s) from {} rule(s).",
                context.analysisRunId(), result.findings().size(), result.rulesEvaluated());

        return result;
    }
}
