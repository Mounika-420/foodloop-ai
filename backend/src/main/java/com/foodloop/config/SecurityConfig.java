package com.foodloop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodloop.dto.ApiError;
import com.foodloop.repository.RevokedTokenRepository;
import com.foodloop.repository.UserRepository;
import com.foodloop.security.AuthUser;
import com.foodloop.security.JwtAuthFilter;
import com.foodloop.security.JwtService;
import com.foodloop.security.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.cors-allowed-origins}")
    private String allowedOrigins;

    @Value("${app.rate-limit.per-minute:10}")
    private int rateLimitPerMinute;

    public SecurityConfig(JwtService jwtService, UserRepository userRepository,
                          RevokedTokenRepository revokedTokenRepository, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /** Defining this bean also stops Spring Boot from creating its default in-memory user. */
    @Bean
    public UserDetailsService userDetailsService() {
        return email -> userRepository.findByEmail(email)
                .map(AuthUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())                       // stateless Bearer-token API, no cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/ngo/**").hasRole("NGO")
                        .requestMatchers("/api/volunteer/**").hasRole("VOLUNTEER")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, 401, "Unauthorized",
                                "Authentication required or token invalid", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, 403, "Forbidden",
                                "You do not have permission to access this resource", req.getRequestURI())))
                .addFilterBefore(new RateLimitFilter(objectMapper, rateLimitPerMinute), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthFilter(jwtService, userRepository, revokedTokenRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private void writeError(HttpServletResponse res, int status, String error, String message, String path) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getOutputStream(),
                new ApiError(LocalDateTime.now().toString(), status, error, message, path, null));
    }
}
