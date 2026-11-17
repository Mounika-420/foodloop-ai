package com.foodloop.dto;

/** Result of a surplus or demand prediction. When the AI service cannot be used, aiAvailable is false and predictedKg is null. */
public record AiPredictionResponse(
        boolean aiAvailable,
        Double predictedKg,
        String modelName,
        String modelVersion,
        Boolean trainedOnSynthetic,
        String confidenceNote,
        Integer latencyMs,
        String message) {
}