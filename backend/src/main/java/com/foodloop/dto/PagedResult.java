package com.foodloop.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResult<T> from(Page<T> p) {
        return new PagedResult<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}