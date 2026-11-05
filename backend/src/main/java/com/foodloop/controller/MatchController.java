package com.foodloop.controller;

import com.foodloop.dto.MatchResponse;
import com.foodloop.dto.NgoOfferResponse;
import com.foodloop.dto.PagedResult;
import com.foodloop.dto.RejectMatchRequest;
import com.foodloop.security.AuthUser;
import com.foodloop.service.MatchingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Matching")
public class MatchController {

    private final MatchingService matchingService;

    public MatchController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping("/donations/{id}/matches")
    @PreAuthorize("hasAnyRole('DONOR','ADMIN')")
    public List<MatchResponse> matches(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return matchingService.matchesForDonation(me.getId(), me.getRole(), id);
    }

    @GetMapping("/ngo/recommended")
    @PreAuthorize("hasRole('NGO')")
    public PagedResult<NgoOfferResponse> recommended(@AuthenticationPrincipal AuthUser me,
                                                     @PageableDefault(size = 10) Pageable pageable) {
        return matchingService.recommended(me.getId(), pageable);
    }

    @PostMapping("/ngo/matches/{matchId}/accept")
    @PreAuthorize("hasRole('NGO')")
    public MatchResponse accept(@AuthenticationPrincipal AuthUser me, @PathVariable Long matchId) {
        return matchingService.accept(me.getId(), matchId);
    }

    @PostMapping("/ngo/matches/{matchId}/reject")
    @PreAuthorize("hasRole('NGO')")
    public MatchResponse reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long matchId,
                                @Valid @RequestBody(required = false) RejectMatchRequest request) {
        return matchingService.reject(me.getId(), matchId, request == null ? null : request.reason());
    }
}