package com.example.smsbackend.entity;

public enum UserRole {
    SUPER_ADMIN(1),
    MANAGER(2),
    USER(3);

    private final int code;

    UserRole(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
