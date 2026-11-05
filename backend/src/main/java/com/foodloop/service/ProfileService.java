package com.foodloop.service;

import com.foodloop.dto.ChangePasswordRequest;
import com.foodloop.dto.ProfileResponse;
import com.foodloop.dto.UpdateProfileRequest;
import com.foodloop.entity.User;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public ProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        return toResponse(find(userId));
    }

    @Transactional
    public ProfileResponse update(Long userId, UpdateProfileRequest r) {
        User u = find(userId);
        u.setFullName(r.fullName().trim());
        u.setPhone(r.phone());
        userRepository.save(u);
        auditService.log(userId, "UPDATE_PROFILE", "USER", userId, null, null);
        return toResponse(u);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest r) {
        User u = find(userId);
        if (!passwordEncoder.matches(r.currentPassword(), u.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (r.currentPassword().equals(r.newPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        u.setPasswordHash(passwordEncoder.encode(r.newPassword()));
        userRepository.save(u);
        auditService.log(userId, "CHANGE_PASSWORD", "USER", userId, null, null);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private ProfileResponse toResponse(User u) {
        return new ProfileResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole().getName(),
                u.getStatus().name(), u.isEmailVerified(), u.getCreatedAt());
    }
}
