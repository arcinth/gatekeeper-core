package com.gatekeeper.github.exception;

import com.gatekeeper.exception.ApiException;
import com.gatekeeper.exception.ErrorCode;

/**
 * A signature-valid webhook whose body either isn't parseable JSON or is
 * missing fields GateKeeper requires (e.g. a "pull_request" event with no
 * pull_request object). Distinct from InvalidWebhookSignatureException: this
 * is a payload-shape problem, not a trust problem, so it maps to 400 rather
 * than 401 (see Sprint 2 Architecture, Section 13: Error Handling Strategy).
 */
public class MalformedWebhookPayloadException extends ApiException {

    public MalformedWebhookPayloadException(String message) {
        super(ErrorCode.GK_400, message);
    }

    public MalformedWebhookPayloadException(String message, Throwable cause) {
        super(ErrorCode.GK_400, message);
        initCause(cause);
    }
}
