package com.clouddisk.service;

import org.springframework.http.HttpStatus;

public class WechatLoginException extends RuntimeException {

    private final int statusCode;

    private WechatLoginException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public static WechatLoginException badRequest(String message) {
        return new WechatLoginException(HttpStatus.BAD_REQUEST.value(), message);
    }

    public static WechatLoginException badGateway(String message) {
        return new WechatLoginException(HttpStatus.BAD_GATEWAY.value(), message);
    }

    public static WechatLoginException conflict(String message) {
        return new WechatLoginException(HttpStatus.CONFLICT.value(), message);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public HttpStatus getStatus() {
        return HttpStatus.valueOf(statusCode);
    }
}
