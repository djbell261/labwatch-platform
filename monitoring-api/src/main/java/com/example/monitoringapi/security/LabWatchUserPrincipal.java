package com.example.monitoringapi.security;

public record LabWatchUserPrincipal(
        Long id,
        String userId,
        String email,
        String displayName,
        String role
) {
}
