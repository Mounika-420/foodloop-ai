package com.foodloop.controller;

import com.foodloop.dto.AdminUserResponse;
import com.foodloop.dto.PagedResult;
import com.foodloop.dto.StatusChangeRequest;
import com.foodloop.dto.VerificationDecisionRequest;
import com.foodloop.dto.VerificationResponse;
import com.foodloop.entity.enums.UserStatus;
import com.foodloop.entity.enums.VerificationStatus;
import com.foodloop.security.AuthUser;
import com.foodloop.service.AdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public PagedResult<AdminUserResponse> users(@RequestParam(defaultValue = "") String role,
                                                @RequestParam(required = false) UserStatus status,
                                                @RequestParam(defaultValue = "") String q,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return adminService.users(role, status, q, pageable);
    }

    @GetMapping("/users/{id}")
    public AdminUserResponse user(@PathVariable Long id) {
        return adminService.user(id);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserResponse changeStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                          @Valid @RequestBody StatusChangeRequest request) {
        return adminService.changeStatus(me.getId(), id, request.status(), request.reason());
    }

    @GetMapping("/verifications")
    public List<VerificationResponse> verifications(@RequestParam(required = false) String type,
                                                    @RequestParam(required = false) VerificationStatus status) {
        return adminService.verifications(type, status);
    }

    @PostMapping("/verifications/{type}/{userId}/approve")
    public VerificationResponse approve(@AuthenticationPrincipal AuthUser me, @PathVariable String type,
                                        @PathVariable Long userId) {
        return adminService.approve(me.getId(), type, userId);
    }

    @PostMapping("/verifications/{type}/{userId}/reject")
    public VerificationResponse reject(@AuthenticationPrincipal AuthUser me, @PathVariable String type,
                                       @PathVariable Long userId,
                                       @Valid @RequestBody(required = false) VerificationDecisionRequest request) {
        return adminService.reject(me.getId(), type, userId, request == null ? null : request.reason());
    }
}