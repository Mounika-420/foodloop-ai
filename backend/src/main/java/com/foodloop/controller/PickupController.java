package com.foodloop.controller;

import com.foodloop.dto.ConfirmDeliveryRequest;
import com.foodloop.dto.CreatePickupRequest;
import com.foodloop.dto.DeliveryStatusRequest;
import com.foodloop.dto.PickupResponse;
import com.foodloop.security.AuthUser;
import com.foodloop.service.PickupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Pickups and deliveries")
public class PickupController {

    private final PickupService pickupService;

    public PickupController(PickupService pickupService) {
        this.pickupService = pickupService;
    }

    @PostMapping("/ngo/pickups")
    @PreAuthorize("hasRole('NGO')")
    @ResponseStatus(HttpStatus.CREATED)
    public PickupResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreatePickupRequest req) {
        return pickupService.create(me.getId(), req);
    }

    @GetMapping("/ngo/pickups")
    @PreAuthorize("hasRole('NGO')")
    public List<PickupResponse> mine(@AuthenticationPrincipal AuthUser me) {
        return pickupService.mineForNgo(me.getId());
    }

    @PostMapping("/ngo/deliveries/{id}/confirm")
    @PreAuthorize("hasRole('NGO')")
    public PickupResponse confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                  @Valid @RequestBody ConfirmDeliveryRequest req) {
        return pickupService.confirm(me.getId(), id, req);
    }

    @GetMapping("/volunteer/tasks/available")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public List<PickupResponse> available(@AuthenticationPrincipal AuthUser me) {
        return pickupService.available(me.getId());
    }

    @GetMapping("/volunteer/tasks/assigned")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public List<PickupResponse> assigned(@AuthenticationPrincipal AuthUser me) {
        return pickupService.assigned(me.getId());
    }

    @PostMapping("/volunteer/pickups/{id}/accept")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public PickupResponse accept(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return pickupService.accept(me.getId(), id);
    }

    @PatchMapping("/volunteer/deliveries/{id}/status")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public PickupResponse updateStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @Valid @RequestBody DeliveryStatusRequest req) {
        return pickupService.updateStatus(me.getId(), id, req);
    }
}