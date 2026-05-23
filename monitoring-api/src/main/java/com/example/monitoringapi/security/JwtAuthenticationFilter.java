package com.example.monitoringapi.security;

import com.example.monitoringapi.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import com.example.monitoringapi.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        try {
            Claims claims = jwtService.parseToken(token);
            String userId = claims.getSubject();
            User user = userRepository.findByUserId(userId).orElse(null);
            if (user != null) {
                String role = jwtService.extractRole(claims).name();
                LabWatchUserPrincipal principal = new LabWatchUserPrincipal(
                        user.getId(),
                        user.getUserId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        role
                );
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_" + role)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put("userId", user.getUserId());
            }
        } catch (ExpiredJwtException exception) {
            SecurityContextHolder.clearContext();
            log.info("event=auth_token_expired path={}", request.getRequestURI());
        } catch (JwtException exception) {
            SecurityContextHolder.clearContext();
            log.warn("event=auth_token_invalid path={} message={}", request.getRequestURI(), exception.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
