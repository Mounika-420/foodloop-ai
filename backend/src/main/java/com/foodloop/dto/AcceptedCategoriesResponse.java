package com.foodloop.dto;

import java.util.List;

public record AcceptedCategoriesResponse(List<Integer> categoryIds, boolean acceptsAll) {
}