package com.example.monitoringapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String userId;
    private String email;
    private String displayName;
    private String role;
    private Instant expiresAt;
}
