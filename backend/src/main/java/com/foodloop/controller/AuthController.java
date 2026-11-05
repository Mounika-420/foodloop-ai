package com.foodloop.controller;

import com.foodloop.dto.AuthResponse;
import com.foodloop.dto.ForgotPasswordRequest;
import com.foodloop.dto.LoginRequest;
import com.foodloop.dto.MessageResponse;
import com.foodloop.dto.RegisterRequest;
import com.foodloop.dto.RegisterResponse;
import com.foodloop.dto.ResetPasswordRequest;
import com.foodloop.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return new MessageResponse("If this email is registered, a password reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Password updated. You can log in with the new password.");
    }
}
