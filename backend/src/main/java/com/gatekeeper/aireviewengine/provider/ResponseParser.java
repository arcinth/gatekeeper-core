package com.gatekeeper.aireviewengine.provider;

import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewengine.exception.AIProviderException;

/**
 * Turns one provider's own raw response shape into the platform's normalized
 * AIReviewResult. The interface boundary here IS the normalization boundary
 * (Sprint 4 Architecture, Section 12): nothing outside a provider's own
 * package ever sees a provider-specific response DTO.
 * <p>
 * Implementations must honor two distinct failure granularities:
 * <ul>
 *   <li>Whole response unparseable or untrustworthy (malformed JSON, missing
 *       required fields, empty content) - throw {@link AIProviderException};
 *       the whole review is recorded as failed, no findings persisted.</li>
 *   <li>A single finding entry within an otherwise-valid response is
 *       malformed (unrecognized type/confidence value, missing a required
 *       field) - skip just that entry, log a warning, keep the rest. This
 *       mirrors the exact fault-isolation philosophy PolicyEngine/SecurityEngine
 *       already apply per-rule, applied here per-finding-within-one-response
 *       instead.</li>
 * </ul>
 */
public interface ResponseParser<TResponse> {

    AIReviewResult parse(AIReviewContext context, TResponse response);
}
