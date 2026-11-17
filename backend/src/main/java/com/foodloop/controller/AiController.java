package com.foodloop.controller;

import com.foodloop.dto.AiPredictionResponse;
import com.foodloop.dto.AiStatusResponse;
import com.foodloop.security.AuthUser;
import com.foodloop.service.AiPredictionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class AiController {

    private final AiPredictionService aiService;

    public AiController(AiPredictionService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/ai/surplus-prediction")
    @PreAuthorize("hasRole('DONOR')")
    public AiPredictionResponse surplus(@AuthenticationPrincipal AuthUser me,
                                        @RequestParam String category,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return aiService.surplus(me.getId(), category, date);
    }

    @GetMapping("/ai/demand-prediction")
    @PreAuthorize("hasAnyRole('NGO','ADMIN')")
    public AiPredictionResponse demand(@AuthenticationPrincipal AuthUser me,
                                       @RequestParam String category,
                                       @RequestParam String area,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(required = false) Integer hour) {
        return aiService.demand(me.getId(), me.getRole(), category, area, date, hour);
    }

    @GetMapping("/ai/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AiStatusResponse status() {
        return aiService.status();
    }
}