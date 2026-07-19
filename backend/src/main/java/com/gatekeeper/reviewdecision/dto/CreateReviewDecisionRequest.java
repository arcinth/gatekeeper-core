package com.gatekeeper.reviewdecision.dto;

import com.gatekeeper.reviewdecision.ReviewDecisionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewDecisionRequest(@NotNull ReviewDecisionType decision, @Size(max = 2000) String comment) {
}
