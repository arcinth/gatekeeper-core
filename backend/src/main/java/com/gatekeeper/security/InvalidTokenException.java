package com.gatekeeper.security;

import com.gatekeeper.exception.ApiException;
import com.gatekeeper.exception.ErrorCode;

public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(ErrorCode.GK_401, message);
    }
}
