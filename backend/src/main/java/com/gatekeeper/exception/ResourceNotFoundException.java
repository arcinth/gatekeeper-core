package com.gatekeeper.exception;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.GK_404, message);
    }
}
