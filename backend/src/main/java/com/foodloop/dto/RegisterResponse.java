package com.foodloop.dto;

public record RegisterResponse(Long id, String email, String role, String status, String message) {
}
