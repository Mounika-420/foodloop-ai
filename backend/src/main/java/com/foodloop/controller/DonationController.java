package com.foodloop.controller;

import com.foodloop.dto.DonationRequest;
import com.foodloop.dto.DonationResponse;
import com.foodloop.dto.PagedResult;
import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.security.AuthUser;
import com.foodloop.service.DonationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/donations")
@Tag(name = "Donations")
public class DonationController {

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DONOR')")
    public DonationResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody DonationRequest request) {
        return donationService.create(me.getId(), request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('DONOR')")
    public PagedResult<DonationResponse> mine(@AuthenticationPrincipal AuthUser me,
                                              @RequestParam(required = false) DonationStatus status,
                                              @RequestParam(defaultValue = "") String q,
                                              @PageableDefault(size = 10) Pageable pageable) {
        return donationService.mine(me.getId(), status, q, pageable);
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('NGO')")
    public PagedResult<DonationResponse> available(@RequestParam(defaultValue = "") String category,
                                                   @RequestParam(defaultValue = "") String q,
                                                   @PageableDefault(size = 10) Pageable pageable) {
        return donationService.available(category, q, pageable);
    }

    @GetMapping("/{id}")
    public DonationResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return donationService.get(me.getId(), me.getRole(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DONOR')")
    public DonationResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                   @Valid @RequestBody DonationRequest request) {
        return donationService.update(me.getId(), id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('DONOR')")
    public DonationResponse cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return donationService.cancel(me.getId(), id);
    }
}