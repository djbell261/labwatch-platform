package com.example.monitoringapi.security;

import com.example.monitoringapi.entity.User;
import com.example.monitoringapi.exception.UnauthorizedException;
import com.example.monitoringapi.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final boolean authEnabled;

    public CurrentUserService(
            UserRepository userRepository,
            @Value("${labwatch.auth.enabled:false}") boolean authEnabled
    ) {
        this.userRepository = userRepository;
        this.authEnabled = authEnabled;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public User getRequiredUser() {
        if (!authEnabled) {
            throw new UnauthorizedException("User authentication is disabled");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LabWatchUserPrincipal principal)) {
            throw new UnauthorizedException("Authentication is required");
        }

        return userRepository.findById(principal.id())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }

    public User getCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LabWatchUserPrincipal principal)) {
            return null;
        }
        return userRepository.findById(principal.id()).orElse(null);
    }
}
