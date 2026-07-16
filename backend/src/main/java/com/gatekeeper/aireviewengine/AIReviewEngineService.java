package com.gatekeeper.aireviewengine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The application-facing boundary around AIReviewEngine. Mirrors
 * PolicyEngineService/SecurityEngineService exactly: this split keeps
 * AIReviewEngine a pure, trivially-unit-testable delegation (provider in,
 * result out, no logging noise to assert around) while this class carries
 * the concern a future caller actually needs - structured before/after
 * logging. No try/catch here either, symmetric with AIReviewEngine: a
 * provider failure propagates through both layers untouched, for the future
 * caller to handle (Sprint 4 Architecture, Section 6).
 * <p>
 * A Spring-managed bean since Sprint 4 Milestone 2, for the same reason as
 * AIReviewEngine - see its Javadoc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIReviewEngineService {

    private final AIReviewEngine aiReviewEngine;

    public AIReviewResult review(AIReviewContext context) {
        log.info("Starting AI review for analysis run {} (repository '{}', {} file(s)).",
                context.analysisRunId(), context.repositoryFullName(), context.changedFiles().size());

        AIReviewResult result = aiReviewEngine.evaluate(context);

        log.info("Completed AI review for analysis run {}: {} finding(s) from provider '{}'.",
                context.analysisRunId(), result.findings().size(), result.provider());

        return result;
    }
}
