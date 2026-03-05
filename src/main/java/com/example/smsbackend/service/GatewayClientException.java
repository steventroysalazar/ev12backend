package com.example.smsbackend.service;

public class GatewayClientException extends RuntimeException {

    private final int statusCode;

    public GatewayClientException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public GatewayClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
