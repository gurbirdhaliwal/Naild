package com.knappi.security.auth;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String firstname;
    private String lastname;
    private String email;
    private String currentpassword;
    private String password;
    private String aureonVoiceId;
}
