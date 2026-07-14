package com.gatekeeper.exception;

public record ApiErrorResponse(boolean success, ApiError error) {

    public record ApiError(String code, String message) {
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ApiError(code, message));
    }
}
