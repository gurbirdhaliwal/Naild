package com.knappi.security.auth;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String token;
    private String password;
}
