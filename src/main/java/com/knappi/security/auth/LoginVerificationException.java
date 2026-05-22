package com.knappi.security.auth;

public class LoginVerificationException extends RuntimeException {
    public LoginVerificationException(String message) {
        super(message);
    }
}
