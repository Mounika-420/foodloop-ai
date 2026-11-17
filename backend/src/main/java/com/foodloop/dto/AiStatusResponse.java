package com.foodloop.dto;

import java.util.List;
import java.util.Map;

public record AiStatusResponse(
        boolean configured,
        boolean reachable,
        boolean keyAccepted,
        String modelVersion,
        Boolean trainedOnSyntheticData,
        Map<String, Boolean> modelsLoaded,
        List<Map<String, Object>> models,
        Integer latencyMs,
        String message) {
}