package com.gatekeeper.orchestration;

import com.gatekeeper.reviewdecision.ReviewDecisionType;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link ReviewDecisionType} to the GitHub Check Run "conclusion"
 * value the "GateKeeper Review" check publishes (Milestone 4).
 * <p>
 * A dedicated class rather than an inline ternary - contrast
 * {@link GitHubCheckRunService}'s own {@code VerdictOutcome} mapping, which
 * is a single line because that enum is fixed at two values by design
 * (Verdict Engine 1.0). {@code ReviewDecisionType} is expected to grow (e.g.
 * {@code CHANGES_REQUESTED}, {@code WAIVED}, {@code ESCALATED}); centralizing
 * the mapping here means adding a new decision type is a compile error in
 * exactly this one place - the switch expression below has no {@code default}
 * branch, so javac enforces exhaustiveness over every {@code ReviewDecisionType}
 * constant - rather than a silent gap wherever the mapping happened to be
 * inlined.
 */
@Component
public class ReviewDecisionConclusionMapper {

    public String toConclusion(ReviewDecisionType decision) {
        return switch (decision) {
            case APPROVED -> "success";
            case REJECTED -> "failure";
        };
    }
}
