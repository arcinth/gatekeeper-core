package com.gatekeeper.github.exception;

import com.gatekeeper.exception.ApiException;
import com.gatekeeper.exception.ErrorCode;

public class InvalidWebhookSignatureException extends ApiException {

    public InvalidWebhookSignatureException(String message) {
        super(ErrorCode.GK_401, message);
    }
}
