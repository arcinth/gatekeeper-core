package com.gatekeeper.exception;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(ErrorCode.GK_401, message);
    }
}
