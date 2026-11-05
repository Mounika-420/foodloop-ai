package com.foodloop.dto;

import jakarta.validation.constraints.Size;

public record RejectMatchRequest(@Size(max = 200) String reason) {
}