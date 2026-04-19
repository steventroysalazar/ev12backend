package com.example.smsbackend.entity;

public enum UserRole {
    SUPER_ADMIN(1),
    COMPANY_ADMIN(2),
    PORTAL_USER(3),
    MOBILE_APP_USER(4);

    private final int code;

    UserRole(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
