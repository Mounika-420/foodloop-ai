package com.foodloop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** An empty list means the NGO accepts every category. */
public record AcceptedCategoriesRequest(@NotNull @Size(max = 20) List<Integer> categoryIds) {
}