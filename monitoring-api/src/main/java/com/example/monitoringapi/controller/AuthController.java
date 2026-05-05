package com.example.monitoringapi.controller;

import com.example.monitoringapi.dto.request.AuthLoginRequest;
import com.example.monitoringapi.dto.request.AuthRegisterRequest;
import com.example.monitoringapi.dto.response.AuthConfigResponse;
import com.example.monitoringapi.dto.response.AuthResponse;
import com.example.monitoringapi.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean authEnabled;

    public AuthController(AuthService authService, @Value("${labwatch.auth.enabled:false}") boolean authEnabled) {
        this.authService = authService;
        this.authEnabled = authEnabled;
    }

    @GetMapping("/config")
    public AuthConfigResponse getConfig() {
        return new AuthConfigResponse(authEnabled);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody AuthRegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthLoginRequest request) {
        return authService.login(request);
    }
}
