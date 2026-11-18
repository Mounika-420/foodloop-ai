package com.foodloop.controller;

import com.foodloop.dto.AcceptedCategoriesRequest;
import com.foodloop.dto.AcceptedCategoriesResponse;
import com.foodloop.service.NgoAcceptedCategoryService;
import com.foodloop.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The food categories this NGO accepts. An empty list means it accepts every category. */
@RestController
@RequestMapping("/api/ngo/categories")
public class NgoCategoryController {

    private final NgoAcceptedCategoryService service;

    public NgoCategoryController(NgoAcceptedCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public AcceptedCategoriesResponse get(@AuthenticationPrincipal AuthUser me) {
        return service.get(me.getId());
    }

    @PutMapping
    public AcceptedCategoriesResponse replace(@AuthenticationPrincipal AuthUser me,
                                              @Valid @RequestBody AcceptedCategoriesRequest request) {
        return service.replace(me.getId(), request.categoryIds());
    }
}