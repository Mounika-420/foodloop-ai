package com.foodloop.security;

import com.foodloop.entity.enums.UserStatus;
import com.foodloop.repository.RevokedTokenRepository;
import com.foodloop.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads "Authorization: Bearer ...", validates the token, rejects revoked tokens (logout) and
 * reloads the user so suspended accounts lose access immediately.
 * Not a Spring bean on purpose: it is created inside SecurityConfig so it is not registered twice.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository,
                         RevokedTokenRepository revokedTokenRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(header.substring(7).trim());
                if (!revokedTokenRepository.existsByJti(claims.getId())) {
                    Long userId = Long.valueOf(claims.getSubject());
                    userRepository.findById(userId)
                            .filter(u -> u.getStatus() == UserStatus.ACTIVE || u.getStatus() == UserStatus.PENDING)
                            .ifPresent(u -> {
                                AuthUser principal = AuthUser.from(u);
                                UsernamePasswordAuthenticationToken auth =
                                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            });
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("Rejected bearer token: {}", e.getClass().getSimpleName());   // never log the token itself
            }
        }
        chain.doFilter(request, response);
    }
}
