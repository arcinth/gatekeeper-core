package com.gatekeeper.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    GK_400(HttpStatus.BAD_REQUEST, "GK-400"),
    GK_401(HttpStatus.UNAUTHORIZED, "GK-401"),
    GK_403(HttpStatus.FORBIDDEN, "GK-403"),
    GK_404(HttpStatus.NOT_FOUND, "GK-404"),
    GK_409(HttpStatus.CONFLICT, "GK-409"),
    GK_422(HttpStatus.UNPROCESSABLE_ENTITY, "GK-422"),
    GK_429(HttpStatus.TOO_MANY_REQUESTS, "GK-429"),
    GK_500(HttpStatus.INTERNAL_SERVER_ERROR, "GK-500");

    private final HttpStatus httpStatus;
    private final String code;

    ErrorCode(HttpStatus httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }
}
