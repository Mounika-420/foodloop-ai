package com.foodloop.controller;

import com.foodloop.dto.HistoryItemResponse;
import com.foodloop.dto.PagedResult;
import com.foodloop.entity.enums.DeliveryStatus;
import com.foodloop.security.AuthUser;
import com.foodloop.service.HistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "History")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/ngo/history")
    @PreAuthorize("hasRole('NGO')")
    public PagedResult<HistoryItemResponse> ngoHistory(@AuthenticationPrincipal AuthUser me,
                                                       @RequestParam(required = false) DeliveryStatus status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return historyService.forNgo(me.getId(), status, page, size);
    }

    @GetMapping("/volunteer/history")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public PagedResult<HistoryItemResponse> volunteerHistory(@AuthenticationPrincipal AuthUser me,
                                                             @RequestParam(required = false) DeliveryStatus status,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return historyService.forVolunteer(me.getId(), status, page, size);
    }
}