package com.foodloop.controller;

import com.foodloop.dto.ChangePasswordRequest;
import com.foodloop.dto.MessageResponse;
import com.foodloop.dto.ProfileResponse;
import com.foodloop.dto.UpdateProfileRequest;
import com.foodloop.security.AuthUser;
import com.foodloop.service.ProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse me(@AuthenticationPrincipal AuthUser me) {
        return profileService.get(me.getId());
    }

    @PutMapping
    public ProfileResponse update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(me.getId(), request);
    }

    @PutMapping("/password")
    public MessageResponse changePassword(@AuthenticationPrincipal AuthUser me,
                                          @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(me.getId(), request);
        return new MessageResponse("Password changed successfully");
    }
}
