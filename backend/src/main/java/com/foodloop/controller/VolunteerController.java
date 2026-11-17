package com.foodloop.controller;

import com.foodloop.dto.AvailabilityRequest;
import com.foodloop.dto.AvailabilityResponse;
import com.foodloop.security.AuthUser;
import com.foodloop.service.VolunteerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Volunteer")
public class VolunteerController {

    private final VolunteerService volunteerService;

    public VolunteerController(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    @GetMapping("/volunteer/availability")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public AvailabilityResponse availability(@AuthenticationPrincipal AuthUser me) {
        return volunteerService.get(me.getId());
    }

    @PutMapping("/volunteer/availability")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public AvailabilityResponse updateAvailability(@AuthenticationPrincipal AuthUser me,
                                                   @Valid @RequestBody AvailabilityRequest request) {
        return volunteerService.update(me.getId(), request);
    }
}