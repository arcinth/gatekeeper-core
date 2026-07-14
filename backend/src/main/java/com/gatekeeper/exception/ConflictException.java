package com.gatekeeper.exception;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.GK_409, message);
    }
}
