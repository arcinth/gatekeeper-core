package com.gatekeeper.orchestration;

/**
 * Marks a failure as attributable specifically to the Security Engine
 * evaluation step, so AnalysisExecutionService#describeFailure can label it
 * "SECURITY_ENGINE_ERROR" rather than the generic "EXECUTION_ERROR" fallback
 * (Security Engine Architecture, Section 11/15). Package-private: purely an
 * internal signal within the orchestration package, never thrown or caught
 * anywhere else - mirrors AnalysisRunReadyForExecutionEvent's visibility for
 * the same reason.
 */
class SecurityEngineExecutionException extends RuntimeException {

    SecurityEngineExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
