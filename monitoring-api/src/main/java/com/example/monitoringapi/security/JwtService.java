package com.example.monitoringapi.security;

import com.example.monitoringapi.entity.User;
import com.example.monitoringapi.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    public record TokenBundle(String token, Instant expiresAt) {
    }

    private final SecretKey secretKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${jwt.secret:dev-only-secret-change-me}") String secret,
            @Value("${jwt.expiration-minutes:60}") long expirationMinutes
    ) {
        this.secretKey = Keys.hmacShaKeyFor(padSecret(secret).getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public TokenBundle generateTokenBundle(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(user.getUserId())
                .claim("email", user.getEmail())
                .claim("displayName", user.getDisplayName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new TokenBundle(token, expiresAt);
    }

    public String generateToken(User user) {
        return generateTokenBundle(user).token();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UserRole extractRole(Claims claims) {
        String value = claims.get("role", String.class);
        if (value == null || value.isBlank()) {
            return UserRole.OPERATOR;
        }

        return UserRole.valueOf(value.trim().toUpperCase());
    }

    private static String padSecret(String secret) {
        String value = secret == null ? "" : secret.trim();
        if (value.length() >= 32) {
            return value;
        }
        return (value + "0123456789abcdefghijklmnopqrstuvwxyz").substring(0, 32);
    }
}
